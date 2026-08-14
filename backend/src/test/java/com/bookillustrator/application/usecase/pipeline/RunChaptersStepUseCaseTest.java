package com.bookillustrator.application.usecase.pipeline;

import com.bookillustrator.application.port.output.ChapterRepository;
import com.bookillustrator.application.port.output.CharacterRepository;
import com.bookillustrator.application.port.output.GeminiGateway;
import com.bookillustrator.application.port.output.PipelineLock;
import com.bookillustrator.application.port.output.ProjectRepository;
import com.bookillustrator.domain.entity.Chapter;
import com.bookillustrator.domain.entity.Character;
import com.bookillustrator.domain.entity.Project;
import com.bookillustrator.domain.enums.PipelineStep;
import com.bookillustrator.domain.exception.GeminiGenerationException;
import com.bookillustrator.domain.exception.IllegalPipelineStateException;
import com.bookillustrator.domain.exception.ProjectNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunChaptersStepUseCaseTest {

    private static final long OWNER_ID = 1L;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private CharacterRepository characterRepository;

    @Mock
    private ChapterRepository chapterRepository;

    @Mock
    private PipelineLock pipelineLock;

    @Mock
    private GeminiGateway geminiGateway;

    @Mock
    private PlatformTransactionManager transactionManager;

    private RunChaptersStepUseCase useCase;

    @BeforeEach
    void setUp() {
        TransactionStatus status = new DefaultTransactionStatus(null, true, true, false, true, null);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(status);
        useCase = new RunChaptersStepUseCase(
                projectRepository, characterRepository, chapterRepository, pipelineLock, geminiGateway,
                transactionManager);
    }

    private static Project onChaptersStep() {
        Project project = new Project(OWNER_ID, "Title", "/book.txt");
        ReflectionTestUtils.setField(project, "id", 1L);
        project.startStep();
        project.completeStep();
        project.advanceToNextStep(); // STYLE -> CHARACTERS
        project.startStep();
        project.completeStep();
        project.advanceToNextStep(); // CHARACTERS -> PORTRAITS
        project.startStep();
        project.completeStep();
        project.advanceToNextStep(); // PORTRAITS -> CHAPTERS, IDLE
        return project;
    }

    private static Project asIfJustAcquired(Project project) {
        Project locked = new Project(project.getUserId(), project.getTitle(), project.getBookTextPath());
        ReflectionTestUtils.setField(locked, "id", project.getId());
        for (int i = 0; i < 3; i++) {
            locked.startStep();
            locked.completeStep();
            locked.advanceToNextStep();
        }
        locked.startStep(); // CHAPTERS IDLE -> RUNNING
        return locked;
    }

    private static Character character(long id, String name) {
        Character character = new Character(1L, name, "a description");
        ReflectionTestUtils.setField(character, "id", id);
        return character;
    }

    @Test
    void happyPathResolvesCharacterNamesAndAdvancesToIllustrations() {
        Project project = onChaptersStep();
        Project lockedProject = asIfJustAcquired(project);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project), Optional.of(lockedProject));
        when(pipelineLock.tryAcquire(1L, "CHAPTERS")).thenReturn(true);
        when(characterRepository.findByProjectId(1L)).thenReturn(
                List.of(character(10L, "Alice"), character(11L, "The Mad Hatter")));
        when(geminiGateway.generateChapters(any())).thenReturn(new GeminiGateway.ChaptersGenerationResult(
                List.of(new GeminiGateway.ChapterDraft("The Tea Party", "a chaotic tea party scene",
                        List.of("Alice", "The Mad Hatter"))),
                "interaction-2"));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PipelineStepResult result = useCase.execute(1L, OWNER_ID);

        assertThat(result.inProgress()).isFalse();
        assertThat(result.project().getCurrentStep()).isEqualTo(PipelineStep.ILLUSTRATIONS);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Chapter>> captor = ArgumentCaptor.forClass(List.class);
        verify(chapterRepository).saveAll(captor.capture());
        Chapter saved = captor.getValue().get(0);
        assertThat(saved.getName()).isEqualTo("The Tea Party");
        assertThat(saved.getCharacterIds()).containsExactly(10L, 11L);
    }

    @Test
    void referencingAnUnknownCharacterNameIsRejectedAsAFailure() {
        Project project = onChaptersStep();
        Project lockedProject = asIfJustAcquired(project);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project), Optional.of(lockedProject));
        when(pipelineLock.tryAcquire(1L, "CHAPTERS")).thenReturn(true);
        when(characterRepository.findByProjectId(1L)).thenReturn(List.of(character(10L, "Alice")));
        when(geminiGateway.generateChapters(any())).thenReturn(new GeminiGateway.ChaptersGenerationResult(
                List.of(new GeminiGateway.ChapterDraft("Scene", "prompt", List.of("Someone Else"))),
                "interaction-2"));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> useCase.execute(1L, OWNER_ID))
                .isInstanceOf(GeminiGenerationException.class)
                .satisfies(e -> assertThat(((GeminiGenerationException) e).getCode()).isEqualTo("INVALID_OUTPUT"));
        verify(chapterRepository, never()).saveAll(any());
        assertThat(lockedProject.getStepState().name()).isEqualTo("FAILED");
    }

    @Test
    void moreThanOneChapterIsRejectedAsAFailure() {
        Project project = onChaptersStep();
        Project lockedProject = asIfJustAcquired(project);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project), Optional.of(lockedProject));
        when(pipelineLock.tryAcquire(1L, "CHAPTERS")).thenReturn(true);
        when(characterRepository.findByProjectId(1L)).thenReturn(List.of(character(10L, "Alice")));
        when(geminiGateway.generateChapters(any())).thenThrow(
                new GeminiGenerationException("INVALID_OUTPUT", "Gemini returned more than 1 chapter.", true));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> useCase.execute(1L, OWNER_ID))
                .isInstanceOf(GeminiGenerationException.class);
        verify(chapterRepository, never()).saveAll(any());
    }

    @Test
    void retryAfterFailureCallsGeminiAgain() {
        Project project = onChaptersStep();
        project.startStep();
        project.failStep("previous attempt failed");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(characterRepository.findByProjectId(1L)).thenReturn(List.of(character(10L, "Alice")));
        when(geminiGateway.generateChapters(any())).thenReturn(new GeminiGateway.ChaptersGenerationResult(
                List.of(new GeminiGateway.ChapterDraft("Scene", "prompt", List.of("Alice"))), "interaction-3"));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PipelineStepResult result = useCase.execute(1L, OWNER_ID);

        assertThat(result.inProgress()).isFalse();
        verify(geminiGateway).generateChapters(any());
    }

    @Test
    void unclassifiedRuntimeExceptionIsStillClassifiedAndPersisted() {
        Project project = onChaptersStep();
        Project lockedProject = asIfJustAcquired(project);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project), Optional.of(lockedProject));
        when(pipelineLock.tryAcquire(1L, "CHAPTERS")).thenReturn(true);
        when(characterRepository.findByProjectId(1L)).thenReturn(List.of(character(10L, "Alice")));
        when(geminiGateway.generateChapters(any())).thenThrow(new NullPointerException("boom"));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> useCase.execute(1L, OWNER_ID))
                .isInstanceOf(GeminiGenerationException.class)
                .satisfies(e -> assertThat(((GeminiGenerationException) e).getCode()).isEqualTo("UPSTREAM_ERROR"));
        assertThat(lockedProject.getStepState().name()).isEqualTo("FAILED");
    }

    @Test
    void secondConcurrentCallReportsInProgressWithoutCallingGemini() {
        Project project = onChaptersStep();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(pipelineLock.tryAcquire(1L, "CHAPTERS")).thenReturn(false);

        PipelineStepResult result = useCase.execute(1L, OWNER_ID);

        assertThat(result.inProgress()).isTrue();
        verify(geminiGateway, never()).generateChapters(any());
    }

    @Test
    void nonexistentProjectGetsProjectNotFound() {
        when(projectRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(999L, OWNER_ID))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void wrongCurrentStepIsRejected() {
        Project project = new Project(OWNER_ID, "Title", "/book.txt"); // still on STYLE
        ReflectionTestUtils.setField(project, "id", 1L);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> useCase.execute(1L, OWNER_ID))
                .isInstanceOf(IllegalPipelineStateException.class);
    }
}
