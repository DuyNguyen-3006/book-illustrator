package com.bookillustrator.application.usecase.pipeline;

import com.bookillustrator.domain.entity.Project;

/** Shared result shape for every per-step use case (RunStyleStepUseCase, etc). */
public record PipelineStepResult(Project project, boolean inProgress) {

    public static PipelineStepResult completed(Project project) {
        return new PipelineStepResult(project, false);
    }

    public static PipelineStepResult inProgress(Project project) {
        return new PipelineStepResult(project, true);
    }
}
