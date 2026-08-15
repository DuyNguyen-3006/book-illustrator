package com.bookillustrator.infrastructure.persistence;

import com.bookillustrator.application.port.output.ChapterRepository;
import com.bookillustrator.domain.entity.Chapter;
import com.bookillustrator.infrastructure.persistence.repository.ChapterJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ChapterRepositoryAdapter implements ChapterRepository {

    private final ChapterJpaRepository jpaRepository;

    public ChapterRepositoryAdapter(ChapterJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<Chapter> findByProjectId(long projectId) {
        return jpaRepository.findByProjectId(projectId);
    }

    @Override
    public List<Chapter> saveAll(List<Chapter> chapters) {
        return jpaRepository.saveAll(chapters);
    }

    @Override
    public List<Chapter> replaceForProject(long projectId, List<Chapter> chapters) {
        // Runs inside the caller's transaction, so the project is never briefly
        // left with no chapter if the insert fails.
        jpaRepository.deleteByProjectId(projectId);
        jpaRepository.flush();
        return jpaRepository.saveAll(chapters);
    }
}
