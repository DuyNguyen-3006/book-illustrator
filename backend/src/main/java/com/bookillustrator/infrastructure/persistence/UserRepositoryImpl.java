package com.bookillustrator.infrastructure.persistence;

import com.bookillustrator.application.port.UserRepository;
import com.bookillustrator.domain.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository jpaRepository;

    public UserRepositoryImpl(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(UserRepositoryImpl::toDomain);
    }

    @Override
    public User create(String email, String name) {
        try {
            UserEntity saved = jpaRepository.save(new UserEntity(email, name));
            return toDomain(saved);
        } catch (DataIntegrityViolationException raceLostToAnotherRequest) {
            // Two concurrent first-logins with the same new email: the other request's
            // insert committed first (Postgres blocks-then-detects on the unique
            // constraint, so by the time we see this exception, its row is committed
            // and visible) — use theirs instead of failing this request.
            return jpaRepository.findByEmail(email)
                    .map(UserRepositoryImpl::toDomain)
                    .orElseThrow(() -> raceLostToAnotherRequest);
        }
    }

    private static User toDomain(UserEntity entity) {
        return new User(entity.getId(), entity.getEmail(), entity.getName());
    }
}
