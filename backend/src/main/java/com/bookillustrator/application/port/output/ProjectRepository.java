package com.bookillustrator.application.port.output;

import com.bookillustrator.domain.entity.Project;

import java.util.List;

/** What the business needs from project storage — no JPA, no Spring. See docs/architecture.md §7. */
public interface ProjectRepository {

    Project create(long userId, String title, String bookTextPath);

    /** Newest first. */
    List<Project> findByUserId(long userId);
}
