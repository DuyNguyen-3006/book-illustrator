package com.bookillustrator.application.usecase.pipeline;

import com.bookillustrator.application.port.output.ProjectRepository;
import com.bookillustrator.domain.entity.Project;
import com.bookillustrator.domain.exception.IllegalPipelineStateException;
import com.bookillustrator.domain.exception.ProjectNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Routes POST /projects/{id}/run-step to whichever step's use case matches the
 * project's current_step — one action button for "the current step" (spec §4.4),
 * not a route per step name.
 */
@Service
public class RunPipelineStepUseCase {

    private final ProjectRepository projectRepository;
    private final RunStyleStepUseCase runStyleStepUseCase;
    private final RunCharactersStepUseCase runCharactersStepUseCase;

    public RunPipelineStepUseCase(
            ProjectRepository projectRepository,
            RunStyleStepUseCase runStyleStepUseCase,
            RunCharactersStepUseCase runCharactersStepUseCase) {
        this.projectRepository = projectRepository;
        this.runStyleStepUseCase = runStyleStepUseCase;
        this.runCharactersStepUseCase = runCharactersStepUseCase;
    }

    public PipelineStepResult execute(long projectId, long requestingUserId, String userProvidedStyle) {
        Project project = projectRepository.findById(projectId)
                .filter(p -> p.getUserId() == requestingUserId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        return switch (project.getCurrentStep()) {
            case STYLE -> runStyleStepUseCase.execute(projectId, requestingUserId, userProvidedStyle);
            case CHARACTERS -> runCharactersStepUseCase.execute(projectId, requestingUserId);
            case PORTRAITS, CHAPTERS, ILLUSTRATIONS -> throw new IllegalPipelineStateException(
                    "step " + project.getCurrentStep() + " isn't implemented yet");
        };
    }
}
