package com.bookillustrator.application.usecase.project;

import com.bookillustrator.application.port.output.ChapterRepository;
import com.bookillustrator.application.port.output.CharacterRepository;
import com.bookillustrator.application.port.output.ImageStorage;
import com.bookillustrator.application.port.output.ProjectRepository;
import com.bookillustrator.domain.entity.Chapter;
import com.bookillustrator.domain.entity.Character;
import com.bookillustrator.domain.exception.ProjectNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * Spec §5.2: generated images are served through our own API, never as filesystem
 * paths handed to the browser. Ownership is re-checked here, not trusted from the URL.
 */
@Service
public class GetGeneratedImageUseCase {

    private final ProjectRepository projectRepository;
    private final CharacterRepository characterRepository;
    private final ChapterRepository chapterRepository;
    private final ImageStorage imageStorage;

    public GetGeneratedImageUseCase(ProjectRepository projectRepository, CharacterRepository characterRepository,
                                     ChapterRepository chapterRepository, ImageStorage imageStorage) {
        this.projectRepository = projectRepository;
        this.characterRepository = characterRepository;
        this.chapterRepository = chapterRepository;
        this.imageStorage = imageStorage;
    }

    /** Empty when the image has not been generated yet — a 404, not an error state. */
    public Optional<ImageStorage.StoredImage> portrait(long projectId, long characterId, long requestingUserId) {
        requireOwnedProject(projectId, requestingUserId);

        return characterRepository.findByProjectId(projectId).stream()
                .filter(character -> Objects.equals(character.getId(), characterId))
                .findFirst()
                .map(Character::getPortraitImagePath)
                .flatMap(imageStorage::read);
    }

    /** Empty when the image has not been generated yet — a 404, not an error state. */
    public Optional<ImageStorage.StoredImage> illustration(long projectId, long chapterId, long requestingUserId) {
        requireOwnedProject(projectId, requestingUserId);

        return chapterRepository.findByProjectId(projectId).stream()
                .filter(chapter -> Objects.equals(chapter.getId(), chapterId))
                .findFirst()
                .map(Chapter::getIllustrationImagePath)
                .flatMap(imageStorage::read);
    }

    /** Same "not found, not forbidden" rule as GetProjectUseCase — no probing for valid ids. */
    private void requireOwnedProject(long projectId, long requestingUserId) {
        projectRepository.findById(projectId)
                .filter(project -> project.getUserId() == requestingUserId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
    }
}
