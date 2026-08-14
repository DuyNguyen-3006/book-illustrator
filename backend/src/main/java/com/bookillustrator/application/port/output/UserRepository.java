package com.bookillustrator.application.port.output;

import com.bookillustrator.domain.entity.User;

import java.util.Optional;

/** What the business needs from user storage — no JPA, no Spring. See docs/architecture.md §7. */
public interface UserRepository {

    Optional<User> findByEmail(String email);

    User create(String email, String name);
}
