package com.bookillustrator.interfaces.rest.response;

import com.bookillustrator.domain.entity.Project;

public record RunStepResponse(
        long projectId,
        String status,
        String currentStep,
        String stepState,
        String style) {

    public static RunStepResponse from(Project project) {
        return new RunStepResponse(
                project.getId(),
                project.getStatus().name(),
                project.getCurrentStep().name(),
                project.getStepState().name(),
                project.getStyle());
    }
}
