package com.bookillustrator.application;

import com.bookillustrator.application.port.UserRepository;
import com.bookillustrator.domain.User;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Spec §4.1: email + name to start. Email exists → load their projects (caller's job,
 * not this use case's). Doesn't exist → create the user. No password, no OAuth.
 */
@Service
public class LoginUseCase {

    // Deliberately simple — not full RFC 5322, just enough to reject obviously
    // malformed input without pulling in a Bean Validation dependency for 2 fields.
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository userRepository;

    public LoginUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Result execute(Command command) {
        String email = command.email() == null ? "" : command.email().strip().toLowerCase();
        String name = command.name() == null ? "" : command.name().strip();

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidLoginException("email must be a valid address");
        }
        if (name.isBlank()) {
            throw new InvalidLoginException("name must not be blank");
        }

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.create(email, name));

        return new Result(user.id(), user.email(), user.name());
    }

    public record Command(String email, String name) {
    }

    public record Result(long userId, String email, String name) {
    }

    public static class InvalidLoginException extends RuntimeException {
        public InvalidLoginException(String message) {
            super(message);
        }
    }
}
