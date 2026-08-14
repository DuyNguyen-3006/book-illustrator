package com.bookillustrator.infrastructure.persistence.repository;

import com.bookillustrator.domain.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectJpaRepository extends JpaRepository<Project, Long> {
}
