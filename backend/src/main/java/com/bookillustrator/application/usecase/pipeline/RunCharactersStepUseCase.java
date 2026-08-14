package com.bookillustrator.application.usecase.pipeline;

import com.bookillustrator.application.port.output.CharacterRepository;
import com.bookillustrator.application.port.output.GeminiGateway;
import com.bookillustrator.application.port.output.PipelineLock;
import com.bookillustrator.application.port.output.ProjectRepository;
import com.bookillustrator.domain.entity.Character;
import com.bookillustrator.domain.entity.Project;
import com.bookillustrator.domain.enums.PipelineStep;
import com.bookillustrator.domain.enums.ResumeAction;
import com.bookillustrator.domain.enums.StepState;
import com.bookillustrator.domain.exception.GeminiGenerationException;
import com.bookillustrator.domain.exception.IllegalPipelineStateException;
import com.bookillustrator.domain.exception.ProjectNotFoundException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Runs the Characters step (spec §03) — up to 2 adult characters, structured JSON.
 * Mirrors {@link RunStyleStepUseCase}'s resume/lock/retry structure; duplicated rather
 * than extracted for now — see issue #15 plan comment (waiting for a 3rd occurrence,
 * Portraits/#16, before generalizing).
 */
@Service
public class RunCharactersStepUseCase {

    private final ProjectRepository projectRepository;
    private final CharacterRepository characterRepository;
    private final PipelineLock pipelineLock;
    private final GeminiGateway geminiGateway;
    private final TransactionTemplate transactionTemplate;

    public RunCharactersStepUseCase(
            ProjectRepository projectRepository,
            CharacterRepository characterRepository,
            PipelineLock pipelineLock,
            GeminiGateway geminiGateway,
            PlatformTransactionManager transactionManager) {
        this.projectRepository = projectRepository;
        this.characterRepository = characterRepository;
        this.pipelineLock = pipelineLock;
        this.geminiGateway = geminiGateway;
        // Deliberately NOT @Transactional on execute()/runStep() — that would hold a DB
        // transaction open across the 10-30s+ Gemini call (PostgresPipelineLock's own
        // javadoc explicitly rules this out). Only the tail-end persistence below —
        // characters + the project's step-advance — needs to commit atomically, since
        // unlike Style (one row, one table) this step writes a child table first; a
        // crash between the two writes would leave orphaned character rows that a
        // reclaimed retry then adds *more* to, breaking the hard 2-character cap
        // (pipeline-rules SKILL.md §4).
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public PipelineStepResult execute(long projectId, long requestingUserId) {
        Project project = projectRepository.findById(projectId)
                .filter(p -> p.getUserId() == requestingUserId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        if (project.getCurrentStep() != PipelineStep.CHARACTERS) {
            throw new IllegalPipelineStateException(
                    "current step is " + project.getCurrentStep() + ", not CHARACTERS");
        }

        ResumeAction action = project.resumeAction(OffsetDateTime.now());
        return switch (action) {
            case IN_PROGRESS -> PipelineStepResult.inProgress(project);
            case ADVANCE -> PipelineStepResult.completed(project);
            case START, RECLAIM_AND_RESTART, SURFACE_ERROR -> runStep(project);
        };
    }

    private PipelineStepResult runStep(Project project) {
        Project locked;
        if (project.getStepState() == StepState.FAILED) {
            try {
                project.startStep();
                locked = projectRepository.save(project);
            } catch (OptimisticLockingFailureException e) {
                Project current = projectRepository.findById(project.getId()).orElseThrow();
                return PipelineStepResult.inProgress(current);
            }
        } else {
            if (!pipelineLock.tryAcquire(project.getId(), project.getCurrentStep().name())) {
                Project current = projectRepository.findById(project.getId()).orElseThrow();
                return PipelineStepResult.inProgress(current);
            }
            locked = projectRepository.findById(project.getId()).orElseThrow();
        }

        GeminiGateway.CharactersGenerationResult result;
        try {
            result = geminiGateway.generateCharacters(
                    new GeminiGateway.CharactersGenerationRequest(locked.getLastTextInteractionId()));
        } catch (RuntimeException e) {
            GeminiGenerationException classified = classify(e);
            locked.failStep(classified.getMessage());
            projectRepository.save(locked);
            throw classified;
        }

        List<Character> characters = result.characters().stream()
                .map(draft -> new Character(locked.getId(), draft.name(), draft.prompt()))
                .toList();

        locked.recordTextInteraction(result.interactionId());
        locked.completeStep();
        locked.advanceToNextStep();

        transactionTemplate.executeWithoutResult(status -> {
            characterRepository.saveAll(characters);
            projectRepository.save(locked);
        });
        return PipelineStepResult.completed(locked);
    }

    private static GeminiGenerationException classify(RuntimeException e) {
        if (e instanceof GeminiGenerationException gge) {
            return gge;
        }
        if (e instanceof HttpClientErrorException client && client.getStatusCode().value() == 429) {
            return new GeminiGenerationException(
                    "RATE_LIMITED", "The AI service is busy. Try again in a moment.", true);
        }
        if (e instanceof HttpClientErrorException) {
            return new GeminiGenerationException(
                    "INVALID_INPUT", "The input for this step was rejected.", false);
        }
        if (e instanceof HttpServerErrorException) {
            return new GeminiGenerationException(
                    "UPSTREAM_ERROR", "The AI service failed. Your progress is saved.", true);
        }
        if (e instanceof ResourceAccessException) {
            return new GeminiGenerationException(
                    "UPSTREAM_TIMEOUT", "The step took too long and was stopped.", true);
        }
        return new GeminiGenerationException(
                "UPSTREAM_ERROR", "The AI service failed. Your progress is saved.", true);
    }
}
