package com.bookillustrator.application.usecase.pipeline;

import com.bookillustrator.application.port.output.CharacterRepository;
import com.bookillustrator.application.port.output.GeminiGateway;
import com.bookillustrator.application.port.output.ImageStorage;
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
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Runs the Portraits step (spec §03) — one image per character, up to 2. Mirrors
 * {@link RunCharactersStepUseCase}'s resume/lock/retry/transactional-tail structure;
 * still duplicated (see issue #15 plan comment — waiting to generalize until the
 * shape is proven across more than text steps too).
 */
@Service
public class RunPortraitsStepUseCase {

    private final ProjectRepository projectRepository;
    private final CharacterRepository characterRepository;
    private final ImageStorage imageStorage;
    private final PipelineLock pipelineLock;
    private final GeminiGateway geminiGateway;
    private final TransactionTemplate transactionTemplate;

    public RunPortraitsStepUseCase(
            ProjectRepository projectRepository,
            CharacterRepository characterRepository,
            ImageStorage imageStorage,
            PipelineLock pipelineLock,
            GeminiGateway geminiGateway,
            PlatformTransactionManager transactionManager) {
        this.projectRepository = projectRepository;
        this.characterRepository = characterRepository;
        this.imageStorage = imageStorage;
        this.pipelineLock = pipelineLock;
        this.geminiGateway = geminiGateway;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public PipelineStepResult execute(long projectId, long requestingUserId) {
        Project project = projectRepository.findById(projectId)
                .filter(p -> p.getUserId() == requestingUserId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        if (project.getCurrentStep() != PipelineStep.PORTRAITS) {
            throw new IllegalPipelineStateException(
                    "current step is " + project.getCurrentStep() + ", not PORTRAITS");
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

        List<Character> characters = characterRepository.findByProjectId(locked.getId());
        List<GeminiGateway.CharacterForPortrait> characterInputs = characters.stream()
                .map(c -> new GeminiGateway.CharacterForPortrait(c.getId(), c.getName(), c.getPrompt()))
                .toList();

        Map<Long, Character> charactersById = characters.stream()
                .collect(Collectors.toMap(Character::getId, c -> c));
        GeminiGateway.PortraitsGenerationResult result;
        try {
            result = geminiGateway.generatePortraits(new GeminiGateway.PortraitsGenerationRequest(
                    locked.getStyle(), characterInputs, locked.getLastImageInteractionId()));
            // Saving each image to disk is part of this same failure boundary — a
            // Gemini success followed by a local write failure (disk full, permission
            // error) must still leave the step FAILED with a persisted message, not
            // stuck RUNNING with a raw 500 (the exact bug class caught in #14/#15).
            for (GeminiGateway.PortraitResult portrait : result.portraits()) {
                String path = imageStorage.save(portrait.imageBytes(), portrait.mimeType());
                charactersById.get(portrait.characterId()).recordPortrait(path);
            }
        } catch (RuntimeException e) {
            GeminiGenerationException classified = classify(e);
            locked.failStep(classified.getMessage());
            projectRepository.save(locked);
            throw classified;
        }

        locked.recordImageInteraction(result.interactionId());
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
