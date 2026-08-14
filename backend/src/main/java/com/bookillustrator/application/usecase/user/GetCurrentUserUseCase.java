package com.bookillustrator.application.usecase.user;

import com.bookillustrator.application.port.output.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Who is this session? The frontend needs it after a refresh — client-side identity
 * caching is not allowed to be the source of truth (frontend-rules §2).
 */
@Service
public class GetCurrentUserUseCase {

    private final UserRepository userRepository;

    public GetCurrentUserUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Empty when the session names a user that no longer exists — the caller answers 401. */
    public Optional<Result> execute(long userId) {
        return userRepository.findById(userId)
                .map(user -> new Result(user.getId(), user.getEmail(), user.getName()));
    }

    public record Result(long userId, String email, String name) {
    }
}
