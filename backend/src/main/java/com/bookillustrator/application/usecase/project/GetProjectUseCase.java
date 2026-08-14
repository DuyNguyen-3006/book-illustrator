package com.bookillustrator.application.usecase.project;

import com.bookillustrator.application.port.output.BookTextStorage;
import com.bookillustrator.application.port.output.ChapterRepository;
import com.bookillustrator.application.port.output.CharacterRepository;
import com.bookillustrator.application.port.output.ProjectRepository;
import com.bookillustrator.domain.entity.Chapter;
import com.bookillustrator.domain.entity.Character;
import com.bookillustrator.domain.entity.Project;
import com.bookillustrator.domain.exception.ProjectNotFoundException;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Spec §4.4 project detail: title, created date, book text (in full), current step, and
 * whatever style/characters/chapters exist so far.
 */
@Service
public class GetProjectUseCase {

    private final ProjectRepository projectRepository;
    private final BookTextStorage bookTextStorage;
    private final CharacterRepository characterRepository;
    private final ChapterRepository chapterRepository;

    public GetProjectUseCase(ProjectRepository projectRepository, BookTextStorage bookTextStorage,
                              CharacterRepository characterRepository, ChapterRepository chapterRepository) {
        this.projectRepository = projectRepository;
        this.bookTextStorage = bookTextStorage;
        this.characterRepository = characterRepository;
        this.chapterRepository = chapterRepository;
    }

    public Result execute(long projectId, long requestingUserId) {
        // Same exception for "doesn't exist" and "exists but isn't yours" — a request
        // can't tell the two apart, so it can't probe for valid ids it doesn't own.
        Project project = projectRepository.findById(projectId)
                .filter(p -> p.getUserId() == requestingUserId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        String bookText = bookTextStorage.read(project.getBookTextPath());

        List<CharacterResult> characters = characterRepository.findByProjectId(projectId).stream()
                .map(GetProjectUseCase::toCharacterResult)
                .toList();
        List<ChapterResult> chapters = chapterRepository.findByProjectId(projectId).stream()
                .map(GetProjectUseCase::toChapterResult)
                .toList();

        return new Result(
                project.getId(),
                project.getTitle(),
                project.getCreatedAt(),
                bookText,
                project.getStatus().name(),
                project.getCurrentStep().name(),
                project.getStepState().name(),
                project.getStyle(),
                characters,
                chapters);
    }

    private static CharacterResult toCharacterResult(Character character) {
        return new CharacterResult(
                character.getId(), character.getName(), character.getPrompt(), character.getPortraitImagePath());
    }

    private static ChapterResult toChapterResult(Chapter chapter) {
        return new ChapterResult(
                chapter.getId(), chapter.getName(), chapter.getPrompt(), chapter.getIllustrationImagePath());
    }

    public record Result(
            long projectId,
            String title,
            OffsetDateTime createdAt,
            String bookText,
            String status,
            String currentStep,
            String stepState,
            String style,
            List<CharacterResult> characters,
            List<ChapterResult> chapters) {
    }

    public record CharacterResult(long id, String name, String prompt, String portraitImagePath) {
    }

    public record ChapterResult(long id, String name, String prompt, String illustrationImagePath) {
    }
}
