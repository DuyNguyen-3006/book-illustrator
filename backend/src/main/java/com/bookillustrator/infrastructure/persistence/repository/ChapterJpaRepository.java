package com.bookillustrator.infrastructure.persistence.repository;

import com.bookillustrator.domain.entity.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChapterJpaRepository extends JpaRepository<Chapter, Long> {

    List<Chapter> findByProjectId(Long projectId);
}
