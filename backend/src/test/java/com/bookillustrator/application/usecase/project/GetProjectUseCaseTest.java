package com.bookillustrator.application.usecase.project;

import com.bookillustrator.application.port.output.BookTextStorage;
import com.bookillustrator.application.port.output.ChapterRepository;
import com.bookillustrator.application.port.output.CharacterRepository;
import com.bookillustrator.application.port.output.ProjectRepository;
import com.bookillustrator.domain.entity.Chapter;
import com.bookillustrator.domain.entity.Character;
import com.bookillustrator.domain.entity.Project;
import com.bookillustrator.domain.exception.ProjectNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GetProjectUseCaseTest {

    private final FakeProjectRepository projectRepository = new FakeProjectRepository();
    private final FakeBookTextStorage bookTextStorage = new FakeBookTextStorage();
    private final FakeCharacterRepository characterRepository = new FakeCharacterRepository();
    private final FakeChapterRepository chapterRepository = new FakeChapterRepository();
    private final GetProjectUseCase useCase =
            new GetProjectUseCase(projectRepository, bookTextStorage, characterRepository, chapterRepository);

    @Test
    void ownerGetsFullDetailWithBookText() {
        Project project = projectRepository.add(1L, "The Wind in the Willows", "/books/1.txt");
        bookTextStorage.contents.put("/books/1.txt", "Once upon a time...");

        GetProjectUseCase.Result result = useCase.execute(project.getId(), 1L);

        assertThat(result.title()).isEqualTo("The Wind in the Willows");
        assertThat(result.bookText()).isEqualTo("Once upon a time...");
        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(result.currentStep()).isEqualTo("STYLE");
        assertThat(result.stepState()).isEqualTo("IDLE");
        assertThat(result.characters()).isEmpty();
        assertThat(result.chapters()).isEmpty();
    }

    @Test
    void includesCharactersAndChaptersWhenPresent() {
        Project project = projectRepository.add(1L, "Title", "/books/1.txt");
        bookTextStorage.contents.put("/books/1.txt", "text");

        Character character = new Character(project.getId(), "Mole", "prompt");
        ReflectionTestUtils.setField(character, "id", 1L);
        characterRepository.byProjectId.put(project.getId(), List.of(character));

        Chapter chapter = new Chapter(project.getId(), "Ch1", "prompt", List.of(1L));
        ReflectionTestUtils.setField(chapter, "id", 1L);
        chapterRepository.byProjectId.put(project.getId(), List.of(chapter));

        GetProjectUseCase.Result result = useCase.execute(project.getId(), 1L);

        assertThat(result.characters()).hasSize(1);
        assertThat(result.characters().get(0).name()).isEqualTo("Mole");
        assertThat(result.chapters()).hasSize(1);
        assertThat(result.chapters().get(0).name()).isEqualTo("Ch1");
    }

    @Test
    void freshProjectHasNoErrorAndNoStepStartTime() {
        Project project = projectRepository.add(1L, "Title", "/books/1.txt");
        bookTextStorage.contents.put("/books/1.txt", "text");

        GetProjectUseCase.Result result = useCase.execute(project.getId(), 1L);

        assertThat(result.lastError()).isNull();
        assertThat(result.stepStartedAt()).isNull();
    }

    @Test
    void failedStepExposesItsErrorAndStartTimeSoTheUiCanRenderThemAfterARefresh() {
        Project project = projectRepository.add(1L, "Title", "/books/1.txt");
        bookTextStorage.contents.put("/books/1.txt", "text");
        project.startStep();
        project.failStep("The AI service is busy. Try again in a moment.");

        GetProjectUseCase.Result result = useCase.execute(project.getId(), 1L);

        assertThat(result.stepState()).isEqualTo("FAILED");
        assertThat(result.lastError()).isEqualTo("The AI service is busy. Try again in a moment.");
        assertThat(result.stepStartedAt()).isNotNull();
    }

    @Test
    void nonOwnerGetsNotFoundNotForbidden() {
        Project project = projectRepository.add(1L, "Title", "/books/1.txt");

        assertThatThrownBy(() -> useCase.execute(project.getId(), 2L))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void nonexistentProjectGetsNotFound() {
        assertThatThrownBy(() -> useCase.execute(999L, 1L))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    private static class FakeProjectRepository implements ProjectRepository {
        private final Map<Long, Project> byId = new HashMap<>();
        private long nextId = 1;

        Project add(long userId, String title, String bookTextPath) {
            Project project = new Project(userId, title, bookTextPath);
            ReflectionTestUtils.setField(project, "id", nextId++);
            byId.put(project.getId(), project);
            return project;
        }

        @Override
        public Project create(long userId, String title, String bookTextPath) {
            throw new UnsupportedOperationException("not needed for this test");
        }

        @Override
        public List<Project> findByUserId(long userId) {
            throw new UnsupportedOperationException("not needed for this test");
        }

        @Override
        public Optional<Project> findById(long id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Project save(Project project) {
            byId.put(project.getId(), project);
            return project;
        }
    }

    private static class FakeBookTextStorage implements BookTextStorage {
        private final Map<String, String> contents = new HashMap<>();

        @Override
        public String save(String bookText) {
            throw new UnsupportedOperationException("not needed for this test");
        }

        @Override
        public String read(String path) {
            return contents.get(path);
        }

        @Override
        public void delete(String path) {
            throw new UnsupportedOperationException("not needed for this test");
        }
    }

    private static class FakeCharacterRepository implements CharacterRepository {
        private final Map<Long, List<Character>> byProjectId = new HashMap<>();

        @Override
        public List<Character> findByProjectId(long projectId) {
            return byProjectId.getOrDefault(projectId, List.of());
        }

        @Override
        public List<Character> saveAll(List<Character> characters) {
            throw new UnsupportedOperationException("not needed for this test");
        }

        @Override
        public List<Character> replaceForProject(long projectId, List<Character> characters) {
            throw new UnsupportedOperationException("not needed for this test");
        }
    }

    private static class FakeChapterRepository implements ChapterRepository {
        private final Map<Long, List<Chapter>> byProjectId = new HashMap<>();

        @Override
        public List<Chapter> findByProjectId(long projectId) {
            return byProjectId.getOrDefault(projectId, List.of());
        }

        @Override
        public List<Chapter> replaceForProject(long projectId, List<Chapter> chapters) {
            throw new UnsupportedOperationException("not needed for this test");
        }
    }
}
