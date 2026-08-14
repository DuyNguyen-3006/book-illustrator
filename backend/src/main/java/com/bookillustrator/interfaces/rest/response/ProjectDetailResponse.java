package com.bookillustrator.interfaces.rest.response;

import com.bookillustrator.application.usecase.project.GetProjectUseCase;

import java.time.OffsetDateTime;
import java.util.List;

public record ProjectDetailResponse(
        long projectId,
        String title,
        OffsetDateTime createdAt,
        String bookText,
        String status,
        String currentStep,
        String stepState,
        String style,
        String lastError,
        OffsetDateTime stepStartedAt,
        List<CharacterItem> characters,
        List<ChapterItem> chapters) {

    public static ProjectDetailResponse from(GetProjectUseCase.Result result) {
        return new ProjectDetailResponse(
                result.projectId(),
                result.title(),
                result.createdAt(),
                result.bookText(),
                result.status(),
                result.currentStep(),
                result.stepState(),
                result.style(),
                result.lastError(),
                result.stepStartedAt(),
                result.characters().stream()
                        .map(character -> CharacterItem.from(character, result.projectId()))
                        .toList(),
                result.chapters().stream()
                        .map(chapter -> ChapterItem.from(chapter, result.projectId()))
                        .toList());
    }

    /** {@code portraitUrl} is an API path, null until the image exists — never a server file path. */
    public record CharacterItem(long id, String name, String prompt, String portraitUrl) {
        static CharacterItem from(GetProjectUseCase.CharacterResult r, long projectId) {
            String url = r.portraitImagePath() == null
                    ? null
                    : "/projects/" + projectId + "/characters/" + r.id() + "/portrait";
            return new CharacterItem(r.id(), r.name(), r.prompt(), url);
        }
    }

    /** {@code illustrationUrl} is an API path, null until the image exists — never a server file path. */
    public record ChapterItem(long id, String name, String prompt, String illustrationUrl) {
        static ChapterItem from(GetProjectUseCase.ChapterResult r, long projectId) {
            String url = r.illustrationImagePath() == null
                    ? null
                    : "/projects/" + projectId + "/chapters/" + r.id() + "/illustration";
            return new ChapterItem(r.id(), r.name(), r.prompt(), url);
        }
    }
}
