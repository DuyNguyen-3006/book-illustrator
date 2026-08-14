package com.bookillustrator.application.usecase.pipeline;

import com.bookillustrator.application.port.output.BookTextStorage;
import com.bookillustrator.application.port.output.GeminiGateway;
import com.bookillustrator.application.port.output.PipelineLock;
import com.bookillustrator.application.port.output.ProjectRepository;
import com.bookillustrator.domain.entity.Project;
import com.bookillustrator.domain.enums.PipelineStep;
import com.bookillustrator.domain.enums.ResumeAction;
import com.bookillustrator.domain.enums.StepState;
import com.bookillustrator.domain.exception.GeminiGenerationException;
import com.bookillustrator.domain.exception.IllegalPipelineStateException;
import com.bookillustrator.domain.exception.ProjectNotFoundException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.OffsetDateTime;

/**
 * Runs the Style step (spec §03) — user-provided or Gemini-generated art style. Spec
 * §4.3: user-driven, no duplicate calls, specific in-progress state, retryable
 * failures. See .claude/skills/pipeline-rules/SKILL.md §2 and §3.
 */
@Service
public class RunStyleStepUseCase {

    private final ProjectRepository projectRepository;
    private final BookTextStorage bookTextStorage;
    private final PipelineLock pipelineLock;
    private final GeminiGateway geminiGateway;

    public RunStyleStepUseCase(
            ProjectRepository projectRepository,
            BookTextStorage bookTextStorage,
            PipelineLock pipelineLock,
            GeminiGateway geminiGateway) {
        this.projectRepository = projectRepository;
        this.bookTextStorage = bookTextStorage;
        this.pipelineLock = pipelineLock;
        this.geminiGateway = geminiGateway;
    }

    public Result execute(long projectId, long requestingUserId, String userProvidedStyle) {
        Project project = projectRepository.findById(projectId)
                .filter(p -> p.getUserId() == requestingUserId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        if (project.getCurrentStep() != PipelineStep.STYLE) {
            throw new IllegalPipelineStateException(
                    "current step is " + project.getCurrentStep() + ", not STYLE");
        }

        ResumeAction action = project.resumeAction(OffsetDateTime.now());
        return switch (action) {
            case IN_PROGRESS -> Result.inProgress(project);
            // Already moved on (e.g. a duplicate/late request) — nothing to do.
            case ADVANCE -> Result.completed(project);
            // Reaching this use case at all only happens via the explicit POST
            // /run-step action — for a FAILED step that action IS the user's retry
            // (pipeline-rules SKILL.md §2: "the user retries via the existing
            // per-step lock"), so run it rather than just re-surfacing the old error.
            case START, RECLAIM_AND_RESTART, SURFACE_ERROR -> runStep(project, userProvidedStyle);
        };
    }

    private Result runStep(Project project, String userProvidedStyle) {
        Project locked;
        if (project.getStepState() == StepState.FAILED) {
            // No concurrent-acquire race to protect against from FAILED — nobody else
            // is running this step — so startStep() runs directly instead of going
            // through the SQL lock (pipeline-rules SKILL.md §2 note). A losing
            // concurrent retry click fails the optimistic-lock check on save() below.
            try {
                project.startStep();
                locked = projectRepository.save(project);
            } catch (OptimisticLockingFailureException e) {
                Project current = projectRepository.findById(project.getId()).orElseThrow();
                return Result.inProgress(current);
            }
        } else {
            if (!pipelineLock.tryAcquire(project.getId(), project.getCurrentStep().name())) {
                // Someone else (or a racing duplicate request) already has it.
                Project current = projectRepository.findById(project.getId()).orElseThrow();
                return Result.inProgress(current);
            }
            // The SQL lock just did the IDLE/RUNNING->RUNNING transition atomically —
            // reload rather than calling project.startStep() again (pipeline-rules §2).
            locked = projectRepository.findById(project.getId()).orElseThrow();
        }

        GeminiGateway.StyleGenerationResult result;
        try {
            result = geminiGateway.generateStyle(new GeminiGateway.StyleGenerationRequest(
                    bookTextStorage.read(locked.getBookTextPath()),
                    locked.getGeminiBookFileUri(),
                    locked.getLastTextInteractionId(),
                    userProvidedStyle));
        } catch (RuntimeException e) {
            // Anything from here on out — a REST error response, a timeout, or a
            // malformed response the client layer couldn't parse — must still leave
            // the step FAILED with a message, never stuck RUNNING (CLAUDE.md §2.2).
            GeminiGenerationException classified = classify(e);
            locked.failStep(classified.getMessage());
            projectRepository.save(locked);
            throw classified;
        }

        locked.recordStyle(result.style());
        locked.recordTextInteraction(result.bookFileUri(), result.interactionId());
        locked.completeStep();
        // Immediately move current_step forward so the UI's stepper/action button
        // point at CHARACTERS next, rather than leaving STYLE sitting at COMPLETED.
        locked.advanceToNextStep();
        projectRepository.save(locked);
        return Result.completed(locked);
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

    public record Result(Project project, boolean inProgress) {
        static Result completed(Project project) {
            return new Result(project, false);
        }

        static Result inProgress(Project project) {
            return new Result(project, true);
        }
    }
}
