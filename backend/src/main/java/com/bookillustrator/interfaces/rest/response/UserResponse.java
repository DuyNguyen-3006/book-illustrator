package com.bookillustrator.interfaces.rest.response;

import com.bookillustrator.application.usecase.user.IdentifyUserUseCase;

public record UserResponse(long userId, String email, String name) {

    public static UserResponse from(IdentifyUserUseCase.Result result) {
        return new UserResponse(result.userId(), result.email(), result.name());
    }
}
