package com.bookillustrator.application.port;

import com.bookillustrator.domain.User;

import java.util.Optional;

public interface UserRepository {

    Optional<User> findByEmail(String email);

    User create(String email, String name);
}
