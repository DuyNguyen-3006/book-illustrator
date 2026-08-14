package com.bookillustrator.application.usecase.project;

import com.bookillustrator.application.port.output.BookTextStorage;
import com.bookillustrator.application.port.output.ProjectRepository;
import com.bookillustrator.domain.entity.Project;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreateProjectUseCaseTest {

    private final FakeBookTextStorage storage = new FakeBookTextStorage();
    private final FakeProjectRepository repository = new FakeProjectRepository();
    private final CreateProjectUseCase useCase = new CreateProjectUseCase(repository, storage);

    @Test
    void createsAProjectAndSavesTheBookTextToStorage() {
        CreateProjectUseCase.Result result = useCase.execute(
                new CreateProjectUseCase.Command(1L, "The Wind in the Willows", "Once upon a time..."));

        assertThat(result.title()).isEqualTo("The Wind in the Willows");
        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(storage.saved).containsExactly("Once upon a time...");
        assertThat(repository.created).hasSize(1);
    }

    @Test
    void blankTitleIsRejected() {
        assertThatThrownBy(() -> useCase.execute(new CreateProjectUseCase.Command(1L, "  ", "text")))
                .isInstanceOf(CreateProjectUseCase.InvalidProjectException.class);
        assertThat(storage.saved).isEmpty();
    }

    @Test
    void tooLongTitleIsRejected() {
        String longTitle = "x".repeat(256);
        assertThatThrownBy(() -> useCase.execute(new CreateProjectUseCase.Command(1L, longTitle, "text")))
                .isInstanceOf(CreateProjectUseCase.InvalidProjectException.class);
    }

    @Test
    void blankBookTextIsRejected() {
        assertThatThrownBy(() -> useCase.execute(new CreateProjectUseCase.Command(1L, "Title", "   ")))
                .isInstanceOf(CreateProjectUseCase.InvalidProjectException.class);
        assertThat(repository.created).isEmpty();
    }

    @Test
    void deletesTheOrphanedFileWhenTheDbInsertFails() {
        FailingProjectRepository failingRepository = new FailingProjectRepository();
        CreateProjectUseCase useCaseWithFailingRepo = new CreateProjectUseCase(failingRepository, storage);

        assertThatThrownBy(() -> useCaseWithFailingRepo.execute(
                new CreateProjectUseCase.Command(1L, "Title", "text")))
                .isInstanceOf(RuntimeException.class);

        assertThat(storage.saved).containsExactly("text");
        assertThat(storage.deleted).containsExactly("/fake/path/1.txt");
    }

    private static class FakeBookTextStorage implements BookTextStorage {
        private final List<String> saved = new ArrayList<>();
        private final List<String> deleted = new ArrayList<>();

        @Override
        public String save(String bookText) {
            saved.add(bookText);
            return "/fake/path/" + saved.size() + ".txt";
        }

        @Override
        public String read(String path) {
            throw new UnsupportedOperationException("not needed for this test");
        }

        @Override
        public void delete(String path) {
            deleted.add(path);
        }
    }

    private static class FakeProjectRepository implements ProjectRepository {
        private final List<Project> created = new ArrayList<>();
        private final AtomicLong nextId = new AtomicLong(1);

        @Override
        public Project create(long userId, String title, String bookTextPath) {
            Project project = new Project(userId, title, bookTextPath);
            ReflectionTestUtils.setField(project, "id", nextId.getAndIncrement());
            created.add(project);
            return project;
        }

        @Override
        public List<Project> findByUserId(long userId) {
            throw new UnsupportedOperationException("not needed for this test");
        }

        @Override
        public java.util.Optional<Project> findById(long id) {
            throw new UnsupportedOperationException("not needed for this test");
        }

        @Override
        public Project save(Project project) {
            throw new UnsupportedOperationException("not needed for this test");
        }
    }

    private static class FailingProjectRepository implements ProjectRepository {
        @Override
        public Project create(long userId, String title, String bookTextPath) {
            throw new RuntimeException("db down");
        }

        @Override
        public java.util.Optional<Project> findById(long id) {
            throw new UnsupportedOperationException("not needed for this test");
        }

        @Override
        public List<Project> findByUserId(long userId) {
            throw new UnsupportedOperationException("not needed for this test");
        }

        @Override
        public Project save(Project project) {
            throw new UnsupportedOperationException("not needed for this test");
        }
    }
}
