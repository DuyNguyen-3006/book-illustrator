package com.bookillustrator.application.port.output;

import com.bookillustrator.domain.entity.Character;

import java.util.List;

public interface CharacterRepository {

    List<Character> findByProjectId(long projectId);

    List<Character> saveAll(List<Character> characters);
}
