package com.bookillustrator.interfaces.rest.response;

import com.bookillustrator.application.usecase.project.GetProjectsUseCase;

import java.time.OffsetDateTime;

public record ProjectSummaryResponse(
        long projectId,
        String title,
        OffsetDateTime createdAt,
        String status,
        String currentStep,
        String stepState) {

    public static ProjectSummaryResponse from(GetProjectsUseCase.Result result) {
        return new ProjectSummaryResponse(
                result.projectId(),
                result.title(),
                result.createdAt(),
                result.status(),
                result.currentStep(),
                result.stepState());
    }
}
