package com.bookillustrator.application.usecase.pipeline;

import com.bookillustrator.application.port.output.CharacterRepository;
import com.bookillustrator.application.port.output.GeminiGateway;
import com.bookillustrator.application.port.output.PipelineLock;
import com.bookillustrator.application.port.output.ProjectRepository;
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
class RunCharactersStepUseCaseTest {

    private static final long OWNER_ID = 1L;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private CharacterRepository characterRepository;

    @Mock
    private PipelineLock pipelineLock;

    @Mock
    private GeminiGateway geminiGateway;

    @Mock
    private PlatformTransactionManager transactionManager;

    private RunCharactersStepUseCase useCase;

    @BeforeEach
    void setUp() {
        TransactionStatus status = new DefaultTransactionStatus(null, true, true, false, true, null);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(status);
        useCase = new RunCharactersStepUseCase(
                projectRepository, characterRepository, pipelineLock, geminiGateway, transactionManager);
    }

    private static Project onCharactersStep() {
        Project project = new Project(OWNER_ID, "Title", "/book.txt");
        ReflectionTestUtils.setField(project, "id", 1L);
        project.startStep();
        project.completeStep();
        project.advanceToNextStep(); // STYLE -> CHARACTERS, IDLE
        return project;
    }

    private static Project asIfJustAcquired(Project project) {
        Project locked = new Project(project.getUserId(), project.getTitle(), project.getBookTextPath());
        ReflectionTestUtils.setField(locked, "id", project.getId());
        locked.startStep();
        locked.completeStep();
        locked.advanceToNextStep(); // STYLE -> CHARACTERS, IDLE
        locked.startStep(); // CHARACTERS IDLE -> RUNNING
        return locked;
    }

    @Test
    void happyPathGeneratesUpToTwoCharactersAndAdvancesToPortraits() {
        Project project = onCharactersStep();
        Project lockedProject = asIfJustAcquired(project);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project), Optional.of(lockedProject));
        when(pipelineLock.tryAcquire(1L, "CHARACTERS")).thenReturn(true);
        when(geminiGateway.generateCharacters(any())).thenReturn(new GeminiGateway.CharactersGenerationResult(
                List.of(new GeminiGateway.CharacterDraft("Alice", "a curious young woman"),
                        new GeminiGateway.CharacterDraft("The Mad Hatter", "an eccentric man in a top hat")),
                "interaction-2"));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PipelineStepResult result = useCase.execute(1L, OWNER_ID);

        assertThat(result.inProgress()).isFalse();
        assertThat(result.project().getCurrentStep()).isEqualTo(PipelineStep.PORTRAITS);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Character>> captor = ArgumentCaptor.forClass(List.class);
        verify(characterRepository).saveAll(captor.capture());
        List<Character> saved = captor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getProjectId()).isEqualTo(1L);
        assertThat(saved.get(0).getName()).isEqualTo("Alice");
        assertThat(saved.get(0).getPrompt()).isEqualTo("a curious young woman");
        assertThat(saved.get(1).getName()).isEqualTo("The Mad Hatter");
    }

    @Test
    void moreThanTwoCharactersIsRejectedAsAFailure() {
        Project project = onCharactersStep();
        Project lockedProject = asIfJustAcquired(project);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project), Optional.of(lockedProject));
        when(pipelineLock.tryAcquire(1L, "CHARACTERS")).thenReturn(true);
        when(geminiGateway.generateCharacters(any())).thenThrow(
                new GeminiGenerationException("INVALID_OUTPUT", "Gemini returned more than 2 characters.", true));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> useCase.execute(1L, OWNER_ID))
                .isInstanceOf(GeminiGenerationException.class)
                .satisfies(e -> assertThat(((GeminiGenerationException) e).getCode()).isEqualTo("INVALID_OUTPUT"));
        verify(characterRepository, never()).saveAll(any());
        assertThat(lockedProject.getStepState().name()).isEqualTo("FAILED");
    }

    @Test
    void retryAfterFailureCallsGeminiAgain() {
        Project project = onCharactersStep();
        project.startStep();
        project.failStep("previous attempt failed");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(geminiGateway.generateCharacters(any())).thenReturn(new GeminiGateway.CharactersGenerationResult(
                List.of(new GeminiGateway.CharacterDraft("Alice", "prompt")), "interaction-3"));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PipelineStepResult result = useCase.execute(1L, OWNER_ID);

        assertThat(result.inProgress()).isFalse();
        verify(geminiGateway).generateCharacters(any());
    }

    @Test
    void secondConcurrentCallReportsInProgressWithoutCallingGemini() {
        Project project = onCharactersStep();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(pipelineLock.tryAcquire(1L, "CHARACTERS")).thenReturn(false);

        PipelineStepResult result = useCase.execute(1L, OWNER_ID);

        assertThat(result.inProgress()).isTrue();
        verify(geminiGateway, never()).generateCharacters(any());
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
