package com.bookillustrator.application.usecase.pipeline;

import com.bookillustrator.application.port.output.ProjectRepository;
import com.bookillustrator.domain.entity.Project;
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
    private final RunPortraitsStepUseCase runPortraitsStepUseCase;
    private final RunChaptersStepUseCase runChaptersStepUseCase;
    private final RunIllustrationsStepUseCase runIllustrationsStepUseCase;

    public RunPipelineStepUseCase(
            ProjectRepository projectRepository,
            RunStyleStepUseCase runStyleStepUseCase,
            RunCharactersStepUseCase runCharactersStepUseCase,
            RunPortraitsStepUseCase runPortraitsStepUseCase,
            RunChaptersStepUseCase runChaptersStepUseCase,
            RunIllustrationsStepUseCase runIllustrationsStepUseCase) {
        this.projectRepository = projectRepository;
        this.runStyleStepUseCase = runStyleStepUseCase;
        this.runCharactersStepUseCase = runCharactersStepUseCase;
        this.runPortraitsStepUseCase = runPortraitsStepUseCase;
        this.runChaptersStepUseCase = runChaptersStepUseCase;
        this.runIllustrationsStepUseCase = runIllustrationsStepUseCase;
    }

    public PipelineStepResult execute(long projectId, long requestingUserId, String userProvidedStyle) {
        Project project = projectRepository.findById(projectId)
                .filter(p -> p.getUserId() == requestingUserId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        return switch (project.getCurrentStep()) {
            case STYLE -> runStyleStepUseCase.execute(projectId, requestingUserId, userProvidedStyle);
            case CHARACTERS -> runCharactersStepUseCase.execute(projectId, requestingUserId);
            case PORTRAITS -> runPortraitsStepUseCase.execute(projectId, requestingUserId);
            case CHAPTERS -> runChaptersStepUseCase.execute(projectId, requestingUserId);
            case ILLUSTRATIONS -> runIllustrationsStepUseCase.execute(projectId, requestingUserId);
        };
    }
}
