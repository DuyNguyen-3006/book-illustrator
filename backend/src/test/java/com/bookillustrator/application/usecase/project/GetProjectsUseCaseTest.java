package com.bookillustrator.application.usecase.project;

import com.bookillustrator.application.port.output.ProjectRepository;
import com.bookillustrator.domain.entity.Project;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GetProjectsUseCaseTest {

    private final FakeProjectRepository repository = new FakeProjectRepository();
    private final GetProjectsUseCase useCase = new GetProjectsUseCase(repository);

    @Test
    void returnsTheUsersProjectsNewestFirst() {
        repository.add(new Project(1L, "First Book", "/a.txt"));
        repository.add(new Project(1L, "Second Book", "/b.txt"));

        List<GetProjectsUseCase.Result> results = useCase.execute(1L);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).title()).isEqualTo("Second Book");
        assertThat(results.get(0).status()).isEqualTo("DRAFT");
        assertThat(results.get(0).currentStep()).isEqualTo("STYLE");
        assertThat(results.get(0).stepState()).isEqualTo("IDLE");
        assertThat(results.get(0).createdAt()).isNotNull();
    }

    @Test
    void returnsEmptyListNotErrorWhenUserHasNoProjects() {
        List<GetProjectsUseCase.Result> results = useCase.execute(1L);

        assertThat(results).isEmpty();
    }

    @Test
    void neverReturnsAnotherUsersProjects() {
        repository.add(new Project(1L, "Mine", "/a.txt"));
        repository.add(new Project(2L, "Someone else's", "/b.txt"));

        List<GetProjectsUseCase.Result> results = useCase.execute(1L);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("Mine");
    }

    private static class FakeProjectRepository implements ProjectRepository {
        private final List<Project> all = new ArrayList<>();
        private long nextId = 1;

        void add(Project project) {
            ReflectionTestUtils.setField(project, "id", nextId++);
            ReflectionTestUtils.setField(project, "createdAt", java.time.OffsetDateTime.now());
            all.add(project);
        }

        @Override
        public Project create(long userId, String title, String bookTextPath) {
            throw new UnsupportedOperationException("not needed for this test");
        }

        @Override
        public List<Project> findByUserId(long userId) {
            // newest first, same contract as ProjectJpaRepository.findByUserIdOrderByCreatedAtDesc
            List<Project> result = new ArrayList<>();
            for (int i = all.size() - 1; i >= 0; i--) {
                if (all.get(i).getUserId() == userId) {
                    result.add(all.get(i));
                }
            }
            return result;
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
}
