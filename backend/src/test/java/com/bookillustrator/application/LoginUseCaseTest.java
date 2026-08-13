package com.bookillustrator.application;

import com.bookillustrator.application.port.UserRepository;
import com.bookillustrator.domain.User;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginUseCaseTest {

    private final FakeUserRepository repository = new FakeUserRepository();
    private final LoginUseCase useCase = new LoginUseCase(repository);

    @Test
    void newEmailCreatesAUser() {
        LoginUseCase.Result result = useCase.execute(new LoginUseCase.Command("a@test.local", "Alice"));

        assertThat(result.email()).isEqualTo("a@test.local");
        assertThat(result.name()).isEqualTo("Alice");
        assertThat(repository.byEmail).hasSize(1);
    }

    @Test
    void existingEmailReturnsTheSameUserInsteadOfCreatingADuplicate() {
        LoginUseCase.Result first = useCase.execute(new LoginUseCase.Command("a@test.local", "Alice"));
        LoginUseCase.Result second = useCase.execute(new LoginUseCase.Command("a@test.local", "Alice again"));

        assertThat(second.userId()).isEqualTo(first.userId());
        assertThat(second.name()).isEqualTo("Alice"); // existing record wins, not overwritten
        assertThat(repository.byEmail).hasSize(1);
    }

    @Test
    void blankNameIsRejected() {
        assertThatThrownBy(() -> useCase.execute(new LoginUseCase.Command("a@test.local", "  ")))
                .isInstanceOf(LoginUseCase.InvalidLoginException.class);
        assertThat(repository.byEmail).isEmpty();
    }

    @Test
    void emailWithoutAtSignIsRejected() {
        assertThatThrownBy(() -> useCase.execute(new LoginUseCase.Command("not-an-email", "Alice")))
                .isInstanceOf(LoginUseCase.InvalidLoginException.class);
        assertThat(repository.byEmail).isEmpty();
    }

    private static class FakeUserRepository implements UserRepository {
        private final Map<String, User> byEmail = new HashMap<>();
        private final AtomicLong nextId = new AtomicLong(1);

        @Override
        public Optional<User> findByEmail(String email) {
            return Optional.ofNullable(byEmail.get(email));
        }

        @Override
        public User create(String email, String name) {
            User user = new User(nextId.getAndIncrement(), email, name);
            byEmail.put(email, user);
            return user;
        }
    }
}
