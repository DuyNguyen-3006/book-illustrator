package com.bookillustrator.application.usecase.pipeline;

import com.bookillustrator.application.port.output.ChapterRepository;
import com.bookillustrator.application.port.output.CharacterRepository;
import com.bookillustrator.application.port.output.GeminiGateway;
import com.bookillustrator.application.port.output.PipelineLock;
import com.bookillustrator.application.port.output.ProjectRepository;
import com.bookillustrator.domain.entity.Chapter;
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
 * Runs the Chapters step (spec §03) — up to 1 chapter, structured JSON, referencing
 * this project's characters. Mirrors {@link RunCharactersStepUseCase}'s
 * resume/lock/retry/transactional-tail structure; still duplicated (see issue #15
 * plan comment).
 */
@Service
public class RunChaptersStepUseCase {

    private final ProjectRepository projectRepository;
    private final CharacterRepository characterRepository;
    private final ChapterRepository chapterRepository;
    private final PipelineLock pipelineLock;
    private final GeminiGateway geminiGateway;
    private final TransactionTemplate transactionTemplate;

    public RunChaptersStepUseCase(
            ProjectRepository projectRepository,
            CharacterRepository characterRepository,
            ChapterRepository chapterRepository,
            PipelineLock pipelineLock,
            GeminiGateway geminiGateway,
            PlatformTransactionManager transactionManager) {
        this.projectRepository = projectRepository;
        this.characterRepository = characterRepository;
        this.chapterRepository = chapterRepository;
        this.pipelineLock = pipelineLock;
        this.geminiGateway = geminiGateway;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public PipelineStepResult execute(long projectId, long requestingUserId) {
        Project project = projectRepository.findById(projectId)
                .filter(p -> p.getUserId() == requestingUserId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        if (project.getCurrentStep() != PipelineStep.CHAPTERS) {
            throw new IllegalPipelineStateException(
                    "current step is " + project.getCurrentStep() + ", not CHAPTERS");
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

        List<Chapter> chapters;
        String interactionId;
        try {
            // Building the name->id lookup is part of this same failure boundary — a
            // rare duplicate character name would otherwise throw before the try block
            // even starts, escaping unclassified (the same bug class caught in #14/#16).
            List<Character> projectCharacters = characterRepository.findByProjectId(locked.getId());
            Map<String, Long> characterIdsByName = projectCharacters.stream()
                    .collect(Collectors.toMap(c -> c.getName().trim().toLowerCase(), Character::getId));

            GeminiGateway.ChaptersGenerationResult result = geminiGateway.generateChapters(
                    new GeminiGateway.ChaptersGenerationRequest(locked.getLastTextInteractionId()));
            chapters = result.chapters().stream()
                    .map(draft -> new Chapter(locked.getId(), draft.name(), draft.prompt(),
                            resolveCharacterIds(draft.characterNames(), characterIdsByName)))
                    .toList();
            interactionId = result.interactionId();
        } catch (RuntimeException e) {
            GeminiGenerationException classified = classify(e);
            locked.failStep(classified.getMessage());
            projectRepository.save(locked);
            throw classified;
        }

        locked.recordTextInteraction(interactionId);
        locked.completeStep();
        locked.advanceToNextStep();

        transactionTemplate.executeWithoutResult(status -> {
            chapterRepository.saveAll(chapters);
            projectRepository.save(locked);
        });
        return PipelineStepResult.completed(locked);
    }

    /**
     * Gemini only knows character names, not our internal ids. A name that doesn't
     * match any of this project's actual characters means the response is
     * inconsistent with reality — treated as an invalid, retriable failure rather than
     * silently dropped, since spec §03 requires chapters to reuse the right portraits.
     */
    private static List<Long> resolveCharacterIds(List<String> names, Map<String, Long> characterIdsByName) {
        return names.stream()
                .map(name -> {
                    Long id = characterIdsByName.get(name.trim().toLowerCase());
                    if (id == null) {
                        throw new GeminiGenerationException("INVALID_OUTPUT",
                                "Gemini referenced a character that doesn't exist in this project: " + name, true);
                    }
                    return id;
                })
                .toList();
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
