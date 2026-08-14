package com.bookillustrator.infrastructure.persistence;

import com.bookillustrator.application.port.output.CharacterRepository;
import com.bookillustrator.domain.entity.Character;
import com.bookillustrator.infrastructure.persistence.repository.CharacterJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CharacterRepositoryAdapter implements CharacterRepository {

    private final CharacterJpaRepository jpaRepository;

    public CharacterRepositoryAdapter(CharacterJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<Character> findByProjectId(long projectId) {
        return jpaRepository.findByProjectId(projectId);
    }

    @Override
    public List<Character> saveAll(List<Character> characters) {
        return jpaRepository.saveAll(characters);
    }

    @Override
    public List<Character> replaceForProject(long projectId, List<Character> characters) {
        // Runs inside the caller's transaction, so a project is never briefly
        // left with no characters if the insert fails.
        jpaRepository.deleteByProjectId(projectId);
        jpaRepository.flush();
        return jpaRepository.saveAll(characters);
    }
}
