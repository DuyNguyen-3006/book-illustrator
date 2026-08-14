package com.bookillustrator.infrastructure.persistence.repository;

import com.bookillustrator.domain.entity.Character;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CharacterJpaRepository extends JpaRepository<Character, Long> {

    List<Character> findByProjectId(Long projectId);
}
