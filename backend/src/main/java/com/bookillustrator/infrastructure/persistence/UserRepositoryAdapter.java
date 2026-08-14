package com.bookillustrator.infrastructure.persistence;

import com.bookillustrator.application.port.output.UserRepository;
import com.bookillustrator.domain.entity.User;
import com.bookillustrator.infrastructure.persistence.repository.UserJpaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;

    public UserRepositoryAdapter(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<User> findById(long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email);
    }

    @Override
    public User create(String email, String name) {
        try {
            return jpaRepository.save(new User(email, name));
        } catch (DataIntegrityViolationException raceLostToAnotherRequest) {
            // Two concurrent first-logins with the same new email: the other request's
            // insert committed first (Postgres blocks-then-detects on the unique
            // constraint, so by the time we see this exception, its row is committed
            // and visible) — use theirs instead of failing this request.
            return jpaRepository.findByEmail(email)
                    .orElseThrow(() -> raceLostToAnotherRequest);
        }
    }
}
