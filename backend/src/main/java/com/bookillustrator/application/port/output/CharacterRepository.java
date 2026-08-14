package com.bookillustrator.application.port.output;

import com.bookillustrator.domain.entity.Character;

import java.util.List;

public interface CharacterRepository {

    List<Character> findByProjectId(long projectId);

    /**
     * The project's characters become exactly these. A step can legitimately run
     * more than once (a reclaimed stale run, a retry after a failure), and adding
     * to what the previous attempt wrote breaks the hard 2-character cap.
     */
    List<Character> replaceForProject(long projectId, List<Character> characters);

    /** Updates rows that already exist, e.g. writing a portrait path onto a character. */
    List<Character> saveAll(List<Character> characters);
}
