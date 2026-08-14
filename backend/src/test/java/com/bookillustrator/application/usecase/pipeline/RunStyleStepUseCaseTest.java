package com.bookillustrator.application.usecase.pipeline;

import com.bookillustrator.application.port.output.BookTextStorage;
import com.bookillustrator.application.port.output.GeminiGateway;
import com.bookillustrator.application.port.output.PipelineLock;
import com.bookillustrator.application.port.output.ProjectRepository;
import com.bookillustrator.domain.entity.Project;
import com.bookillustrator.domain.enums.PipelineStep;
import com.bookillustrator.domain.exception.GeminiGenerationException;
import com.bookillustrator.domain.exception.IllegalPipelineStateException;
import com.bookillustrator.domain.exception.ProjectNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunStyleStepUseCaseTest {

    private static final long OWNER_ID = 1L;
    private static final long OTHER_USER_ID = 2L;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private BookTextStorage bookTextStorage;

    @Mock
    private PipelineLock pipelineLock;

    @Mock
    private GeminiGateway geminiGateway;

    private RunStyleStepUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RunStyleStepUseCase(projectRepository, bookTextStorage, pipelineLock, geminiGateway);
    }

    private static Project freshProject() {
        Project project = new Project(OWNER_ID, "Title", "/book.txt");
        ReflectionTestUtils.setField(project, "id", 1L);
        return project;
    }

    /**
     * In production, pipelineLock.tryAcquire() is a real SQL UPDATE — the reload right
     * after it succeeds sees step_state already RUNNING. Since the lock is mocked here,
     * this stands in for that DB-side effect: same project, but as it would look after
     * a successful acquire.
     */
    private static Project asIfJustAcquired(Project project) {
        Project locked = new Project(project.getUserId(), project.getTitle(), project.getBookTextPath());
        ReflectionTestUtils.setField(locked, "id", project.getId());
        locked.startStep();
        return locked;
    }

    @Test
    void happyPathAcquiresLockCallsGeminiCompletesAndAdvances() {
        Project project = freshProject();
        Project lockedProject = asIfJustAcquired(project);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project), Optional.of(lockedProject));
        when(pipelineLock.tryAcquire(1L, "STYLE")).thenReturn(true);
        when(bookTextStorage.read("/book.txt")).thenReturn("book text");
        when(geminiGateway.generateStyle(any())).thenReturn(
                new GeminiGateway.StyleGenerationResult("Watercolor style", "files/book-uri", "interaction-1"));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PipelineStepResult result = useCase.execute(1L, OWNER_ID, null);

        assertThat(result.inProgress()).isFalse();
        assertThat(result.project().getStyle()).isEqualTo("Watercolor style");
        assertThat(result.project().getCurrentStep()).isEqualTo(PipelineStep.CHARACTERS);
        assertThat(result.project().getGeminiBookFileUri()).isEqualTo("files/book-uri");
        verify(projectRepository).save(any());
    }

    /**
     * backend-rules SKILL.md §3, "Idempotency": a late response from a reclaimed
     * attempt must not double-write results. Simulates a straggler — this attempt's
     * Gemini call finally returns after a reclaimed attempt already advanced the same
     * row — by having the final save() hit the row's real {@code @Version} check
     * (Hibernate throws on any concurrent modification, this scenario included).
     * Asserts the straggler's write is rejected outright rather than silently
     * succeeding with stale data — never that it quietly "wins".
     */
    @Test
    void aLateResponseFromAReclaimedAttemptDoesNotSilentlySucceed() {
        Project project = freshProject();
        Project lockedProject = asIfJustAcquired(project);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project), Optional.of(lockedProject));
        when(pipelineLock.tryAcquire(1L, "STYLE")).thenReturn(true);
        when(bookTextStorage.read("/book.txt")).thenReturn("book text");
        when(geminiGateway.generateStyle(any())).thenReturn(
                new GeminiGateway.StyleGenerationResult("Watercolor style", "files/book-uri", "interaction-1"));
        // Stands in for "a reclaimed attempt already advanced this row" — the final
        // save sees a version conflict instead of quietly overwriting newer data.
        when(projectRepository.save(lockedProject)).thenThrow(
                new org.springframework.orm.ObjectOptimisticLockingFailureException(Project.class, 1L));

        assertThatThrownBy(() -> useCase.execute(1L, OWNER_ID, null))
                .isInstanceOf(org.springframework.dao.OptimisticLockingFailureException.class);
    }

    @Test
    void secondConcurrentCallReportsInProgressWithoutCallingGemini() {
        Project project = freshProject();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(pipelineLock.tryAcquire(1L, "STYLE")).thenReturn(false);

        PipelineStepResult result = useCase.execute(1L, OWNER_ID, null);

        assertThat(result.inProgress()).isTrue();
        verify(geminiGateway, never()).generateStyle(any());
    }

    @Test
    void alreadyRunningWithinTtlReportsInProgressWithoutTryingTheLock() {
        Project project = freshProject();
        project.startStep(); // step_state RUNNING, step_started_at = now
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        PipelineStepResult result = useCase.execute(1L, OWNER_ID, null);

        assertThat(result.inProgress()).isTrue();
        verify(pipelineLock, never()).tryAcquire(anyLong(), anyString());
        verify(geminiGateway, never()).generateStyle(any());
    }

    @Test
    void retryAfterFailureCallsGeminiAgainAndCanSucceed() {
        Project project = freshProject();
        project.startStep();
        project.failStep("previous attempt failed: rate limited");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(bookTextStorage.read("/book.txt")).thenReturn("book text");
        when(geminiGateway.generateStyle(any())).thenReturn(
                new GeminiGateway.StyleGenerationResult("Retried style", "files/book-uri", "interaction-1"));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PipelineStepResult result = useCase.execute(1L, OWNER_ID, null);

        assertThat(result.inProgress()).isFalse();
        assertThat(result.project().getStyle()).isEqualTo("Retried style");
        assertThat(result.project().getCurrentStep()).isEqualTo(PipelineStep.CHARACTERS);
        verify(geminiGateway).generateStyle(any());
    }

    @Test
    void concurrentRetryLoserReportsInProgressOnOptimisticLockConflict() {
        Project project = freshProject();
        project.startStep();
        project.failStep("previous attempt failed");
        Project reloadedAfterWinnerCommitted = asIfJustAcquired(project);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project), Optional.of(reloadedAfterWinnerCommitted));
        when(projectRepository.save(project)).thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(Project.class, 1L));

        PipelineStepResult result = useCase.execute(1L, OWNER_ID, null);

        assertThat(result.inProgress()).isTrue();
        verify(geminiGateway, never()).generateStyle(any());
    }

    @Test
    void rateLimitedGeminiFailureIsClassifiedAndPersisted() {
        Project project = freshProject();
        Project lockedProject = asIfJustAcquired(project);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project), Optional.of(lockedProject));
        when(pipelineLock.tryAcquire(1L, "STYLE")).thenReturn(true);
        when(bookTextStorage.read("/book.txt")).thenReturn("text");
        when(geminiGateway.generateStyle(any())).thenThrow(
                HttpClientErrorException.create(HttpStatusCode.valueOf(429), "Too Many Requests",
                        null, null, null));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> useCase.execute(1L, OWNER_ID, null))
                .isInstanceOf(GeminiGenerationException.class)
                .satisfies(e -> {
                    GeminiGenerationException gge = (GeminiGenerationException) e;
                    assertThat(gge.getCode()).isEqualTo("RATE_LIMITED");
                    assertThat(gge.isRetriable()).isTrue();
                });
        assertThat(lockedProject.getStepState().name()).isEqualTo("FAILED");
        verify(projectRepository).save(lockedProject);
    }

    @Test
    void serverErrorGeminiFailureMapsToUpstreamError() {
        Project project = freshProject();
        Project lockedProject = asIfJustAcquired(project);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project), Optional.of(lockedProject));
        when(pipelineLock.tryAcquire(1L, "STYLE")).thenReturn(true);
        when(bookTextStorage.read("/book.txt")).thenReturn("text");
        when(geminiGateway.generateStyle(any())).thenThrow(
                HttpServerErrorException.create(HttpStatusCode.valueOf(503), "Service Unavailable",
                        null, null, null));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> useCase.execute(1L, OWNER_ID, null))
                .isInstanceOf(GeminiGenerationException.class)
                .satisfies(e -> assertThat(((GeminiGenerationException) e).getCode()).isEqualTo("UPSTREAM_ERROR"));
    }

    @Test
    void unclassifiedRuntimeExceptionFromGeminiIsStillClassifiedAndPersisted() {
        // Reproduces a real bug caught via live testing (issue #14): a plain
        // NullPointerException from the client layer (not an HTTP exception type)
        // used to escape this use case uncaught, leaving the row stuck RUNNING with
        // no persisted error instead of FAILED.
        Project project = freshProject();
        Project lockedProject = asIfJustAcquired(project);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project), Optional.of(lockedProject));
        when(pipelineLock.tryAcquire(1L, "STYLE")).thenReturn(true);
        when(bookTextStorage.read("/book.txt")).thenReturn("text");
        when(geminiGateway.generateStyle(any())).thenThrow(new NullPointerException("boom"));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> useCase.execute(1L, OWNER_ID, null))
                .isInstanceOf(GeminiGenerationException.class)
                .satisfies(e -> assertThat(((GeminiGenerationException) e).getCode()).isEqualTo("UPSTREAM_ERROR"));
        assertThat(lockedProject.getStepState().name()).isEqualTo("FAILED");
        verify(projectRepository).save(lockedProject);
    }

    @Test
    void nonOwnerGetsProjectNotFound() {
        Project project = freshProject();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> useCase.execute(1L, OTHER_USER_ID, null))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void nonexistentProjectGetsProjectNotFound() {
        when(projectRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(999L, OWNER_ID, null))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void wrongCurrentStepIsRejected() {
        Project project = freshProject();
        project.startStep();
        project.completeStep();
        project.advanceToNextStep(); // now on CHARACTERS
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> useCase.execute(1L, OWNER_ID, null))
                .isInstanceOf(IllegalPipelineStateException.class);
    }
}
