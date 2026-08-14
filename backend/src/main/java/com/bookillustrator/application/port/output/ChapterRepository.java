package com.bookillustrator.application.port.output;

import com.bookillustrator.domain.entity.Chapter;

import java.util.List;

public interface ChapterRepository {

    List<Chapter> findByProjectId(long projectId);
}
