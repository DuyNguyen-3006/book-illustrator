package com.bookillustrator.interfaces.rest.response;

import com.bookillustrator.application.usecase.project.CreateProjectUseCase;

public record ProjectResponse(long projectId, String title, String status) {

    public static ProjectResponse from(CreateProjectUseCase.Result result) {
        return new ProjectResponse(result.projectId(), result.title(), result.status());
    }
}
