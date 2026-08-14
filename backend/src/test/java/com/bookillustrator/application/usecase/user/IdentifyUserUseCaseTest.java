package com.bookillustrator.application.usecase.user;

import com.bookillustrator.application.port.output.UserRepository;
import com.bookillustrator.domain.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentifyUserUseCaseTest {

    private final FakeUserRepository repository = new FakeUserRepository();
    private final IdentifyUserUseCase useCase = new IdentifyUserUseCase(repository);

    @Test
    void newEmailCreatesAUser() {
        IdentifyUserUseCase.Result result = useCase.execute(new IdentifyUserUseCase.Command("a@test.local", "Alice"));

        assertThat(result.email()).isEqualTo("a@test.local");
        assertThat(result.name()).isEqualTo("Alice");
        assertThat(repository.byEmail).hasSize(1);
    }

    @Test
    void existingEmailReturnsTheSameUserInsteadOfCreatingADuplicate() {
        IdentifyUserUseCase.Result first = useCase.execute(new IdentifyUserUseCase.Command("a@test.local", "Alice"));
        IdentifyUserUseCase.Result second =
                useCase.execute(new IdentifyUserUseCase.Command("a@test.local", "Alice again"));

        assertThat(second.userId()).isEqualTo(first.userId());
        assertThat(second.name()).isEqualTo("Alice"); // existing record wins, not overwritten
        assertThat(repository.byEmail).hasSize(1);
    }

    @Test
    void blankNameIsRejected() {
        assertThatThrownBy(() -> useCase.execute(new IdentifyUserUseCase.Command("a@test.local", "  ")))
                .isInstanceOf(IdentifyUserUseCase.InvalidLoginException.class);
        assertThat(repository.byEmail).isEmpty();
    }

    @Test
    void emailWithoutAtSignIsRejected() {
        assertThatThrownBy(() -> useCase.execute(new IdentifyUserUseCase.Command("not-an-email", "Alice")))
                .isInstanceOf(IdentifyUserUseCase.InvalidLoginException.class);
        assertThat(repository.byEmail).isEmpty();
    }

    private static class FakeUserRepository implements UserRepository {
        private final Map<String, User> byEmail = new HashMap<>();
        private final AtomicLong nextId = new AtomicLong(1);

        @Override
        public Optional<User> findById(long id) {
            return byEmail.values().stream()
                    .filter(user -> user.getId() != null && user.getId() == id)
                    .findFirst();
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return Optional.ofNullable(byEmail.get(email));
        }

        @Override
        public User create(String email, String name) {
            User user = new User(email, name);
            ReflectionTestUtils.setField(user, "id", nextId.getAndIncrement());
            byEmail.put(email, user);
            return user;
        }
    }
}
