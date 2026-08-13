package com.bookillustrator.domain;

/** A registered user. Plain domain record — no framework, no JPA. */
public record User(long id, String email, String name) {
}
