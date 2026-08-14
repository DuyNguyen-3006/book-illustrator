package com.bookillustrator.application.usecase.project;

import com.bookillustrator.application.port.output.BookTextStorage;
import com.bookillustrator.application.port.output.ProjectRepository;
import com.bookillustrator.domain.entity.Project;
import org.springframework.stereotype.Service;

/**
 * Spec §4.2: create a project from a book's text (paste or .txt upload, resolved by the
 * caller before this point — this use case doesn't know which) plus a title.
 */
@Service
public class CreateProjectUseCase {

    private static final int MAX_TITLE_LENGTH = 255; // matches projects.title VARCHAR(255)

    private final ProjectRepository projectRepository;
    private final BookTextStorage bookTextStorage;

    public CreateProjectUseCase(ProjectRepository projectRepository, BookTextStorage bookTextStorage) {
        this.projectRepository = projectRepository;
        this.bookTextStorage = bookTextStorage;
    }

    public Result execute(Command command) {
        String title = command.title() == null ? "" : command.title().strip();
        String bookText = command.bookText() == null ? "" : command.bookText();

        if (title.isBlank()) {
            throw new InvalidProjectException("title must not be blank");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new InvalidProjectException("title must be at most " + MAX_TITLE_LENGTH + " characters");
        }
        if (bookText.isBlank()) {
            throw new InvalidProjectException("book text must not be blank");
        }

        String bookTextPath = bookTextStorage.save(bookText);
        Project project;
        try {
            project = projectRepository.create(command.userId(), title, bookTextPath);
        } catch (RuntimeException dbFailure) {
            // The file already landed on disk; the DB row that would reference it
            // didn't — clean it up rather than leave an orphan.
            bookTextStorage.delete(bookTextPath);
            throw dbFailure;
        }

        return new Result(project.getId(), project.getTitle(), project.getStatus().name());
    }

    public record Command(long userId, String title, String bookText) {
    }

    public record Result(long projectId, String title, String status) {
    }

    public static class InvalidProjectException extends RuntimeException {
        public InvalidProjectException(String message) {
            super(message);
        }
    }
}
