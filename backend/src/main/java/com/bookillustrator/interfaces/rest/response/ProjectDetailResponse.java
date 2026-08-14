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
                result.characters().stream().map(CharacterItem::from).toList(),
                result.chapters().stream().map(ChapterItem::from).toList());
    }

    public record CharacterItem(long id, String name, String prompt, String portraitImagePath) {
        static CharacterItem from(GetProjectUseCase.CharacterResult r) {
            return new CharacterItem(r.id(), r.name(), r.prompt(), r.portraitImagePath());
        }
    }

    public record ChapterItem(long id, String name, String prompt, String illustrationImagePath) {
        static ChapterItem from(GetProjectUseCase.ChapterResult r) {
            return new ChapterItem(r.id(), r.name(), r.prompt(), r.illustrationImagePath());
        }
    }
}
