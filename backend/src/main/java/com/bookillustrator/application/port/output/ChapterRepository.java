package com.bookillustrator.application.port.output;

import com.bookillustrator.domain.entity.Chapter;

import java.util.List;

public interface ChapterRepository {

    List<Chapter> findByProjectId(long projectId);

    /**
     * The project's chapters become exactly these. Same reason as
     * {@link CharacterRepository#replaceForProject}: a rerun must not push the
     * project past its 1-chapter cap.
     */
    List<Chapter> replaceForProject(long projectId, List<Chapter> chapters);

    /** Updates rows that already exist, e.g. writing an illustration path onto a chapter. */
    List<Chapter> saveAll(List<Chapter> chapters);
}
