package com.bookillustrator.controller.dto;

import com.bookillustrator.application.LoginUseCase;

public record UserResponse(long userId, String email, String name) {

    public static UserResponse from(LoginUseCase.Result result) {
        return new UserResponse(result.userId(), result.email(), result.name());
    }
}
