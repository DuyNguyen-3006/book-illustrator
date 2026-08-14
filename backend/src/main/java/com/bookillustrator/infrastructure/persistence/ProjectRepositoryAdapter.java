package com.bookillustrator.infrastructure.persistence;

import com.bookillustrator.application.port.output.ProjectRepository;
import com.bookillustrator.domain.entity.Project;
import com.bookillustrator.infrastructure.persistence.repository.ProjectJpaRepository;
import org.springframework.stereotype.Component;

@Component
public class ProjectRepositoryAdapter implements ProjectRepository {

    private final ProjectJpaRepository jpaRepository;

    public ProjectRepositoryAdapter(ProjectJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Project create(long userId, String title, String bookTextPath) {
        return jpaRepository.save(new Project(userId, title, bookTextPath));
    }
}
