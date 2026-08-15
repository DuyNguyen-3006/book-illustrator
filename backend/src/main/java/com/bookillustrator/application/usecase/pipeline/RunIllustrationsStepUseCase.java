package com.bookillustrator.application.usecase.pipeline;

import com.bookillustrator.application.port.output.ChapterRepository;
import com.bookillustrator.application.port.output.CharacterRepository;
import com.bookillustrator.application.port.output.GeminiGateway;
import com.bookillustrator.application.port.output.ImageStorage;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Runs the Illustrations step (spec §03) — one scene per chapter, reusing the
 * portraits so the characters stay recognisable. Same resume/lock/retry/transactional
 * -tail shape as {@link RunPortraitsStepUseCase}; still duplicated per step rather
 * than generalised, for the reason given there.
 */
@Service
public class RunIllustrationsStepUseCase {

    private final ProjectRepository projectRepository;
    private final ChapterRepository chapterRepository;
    private final CharacterRepository characterRepository;
    private final ImageStorage imageStorage;
    private final PipelineLock pipelineLock;
    private final GeminiGateway geminiGateway;
    private final TransactionTemplate transactionTemplate;

    public RunIllustrationsStepUseCase(
            ProjectRepository projectRepository,
            ChapterRepository chapterRepository,
            CharacterRepository characterRepository,
            ImageStorage imageStorage,
            PipelineLock pipelineLock,
            GeminiGateway geminiGateway,
            PlatformTransactionManager transactionManager) {
        this.projectRepository = projectRepository;
        this.chapterRepository = chapterRepository;
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

        if (project.getCurrentStep() != PipelineStep.ILLUSTRATIONS) {
            throw new IllegalPipelineStateException(
                    "current step is " + project.getCurrentStep() + ", not ILLUSTRATIONS");
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

        List<Chapter> chapters = chapterRepository.findByProjectId(locked.getId());
        Map<Long, Chapter> chaptersById = chapters.stream()
                .collect(Collectors.toMap(Chapter::getId, c -> c));
        Map<Long, Character> charactersById = characterRepository.findByProjectId(locked.getId()).stream()
                .collect(Collectors.toMap(Character::getId, c -> c));

        try {
            List<GeminiGateway.ChapterForIllustration> inputs = chapters.stream()
                    .map(chapter -> new GeminiGateway.ChapterForIllustration(
                            chapter.getId(), chapter.getName(), chapter.getPrompt(),
                            portraitsFor(chapter, charactersById)))
                    .toList();

            GeminiGateway.IllustrationsGenerationResult result =
                    geminiGateway.generateIllustrations(
                            new GeminiGateway.IllustrationsGenerationRequest(locked.getStyle(), inputs));

            // Writing each image is inside the same failure boundary as the call: a
            // Gemini success followed by a failed local write must still leave the step
            // FAILED with a message, not stuck RUNNING behind a raw 500.
            for (GeminiGateway.IllustrationResult illustration : result.illustrations()) {
                String path = imageStorage.save(illustration.imageBytes(), illustration.mimeType());
                chaptersById.get(illustration.chapterId()).recordIllustration(path);
            }
        } catch (RuntimeException e) {
            GeminiGenerationException classified = classify(e);
            locked.failStep(classified.getMessage());
            projectRepository.save(locked);
            throw classified;
        }

        locked.completeStep();
        locked.advanceToNextStep(); // last step: this marks the project COMPLETED

        transactionTemplate.executeWithoutResult(status -> {
            chapterRepository.saveAll(chapters);
            projectRepository.save(locked);
        });
        return PipelineStepResult.completed(locked);
    }

    /**
     * Only the portraits this chapter names, and only ones that exist. A character
     * without a portrait is skipped rather than failing the step: the scene is still
     * worth generating from the prompt alone.
     */
    private List<GeminiGateway.CharacterPortrait> portraitsFor(
            Chapter chapter, Map<Long, Character> charactersById) {
        List<GeminiGateway.CharacterPortrait> portraits = new ArrayList<>();
        if (chapter.getCharacterIds() == null) {
            return portraits;
        }
        for (Long characterId : chapter.getCharacterIds()) {
            Character character = charactersById.get(characterId);
            if (character == null || character.getPortraitImagePath() == null) {
                continue;
            }
            imageStorage.read(character.getPortraitImagePath()).ifPresent(stored ->
                    portraits.add(new GeminiGateway.CharacterPortrait(
                            character.getName(), stored.bytes(), stored.mimeType())));
        }
        return portraits;
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
        if (e instanceof ResourceAccessException) {
            return new GeminiGenerationException(
                    "UPSTREAM_TIMEOUT", "The step took too long and was stopped.", true);
        }
        if (e instanceof HttpServerErrorException) {
            return new GeminiGenerationException(
                    "UPSTREAM_ERROR", "The AI service failed. Your progress is saved.", true);
        }
        return new GeminiGenerationException(
                "UPSTREAM_ERROR", "The AI service failed. Your progress is saved.", true);
    }
}
