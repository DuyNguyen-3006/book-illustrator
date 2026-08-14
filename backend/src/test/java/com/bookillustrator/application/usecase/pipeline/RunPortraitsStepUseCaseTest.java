package com.bookillustrator.application.usecase.pipeline;

import com.bookillustrator.application.port.output.CharacterRepository;
import com.bookillustrator.application.port.output.GeminiGateway;
import com.bookillustrator.application.port.output.ImageStorage;
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
class RunPortraitsStepUseCaseTest {

    private static final long OWNER_ID = 1L;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private CharacterRepository characterRepository;

    @Mock
    private ImageStorage imageStorage;

    @Mock
    private PipelineLock pipelineLock;

    @Mock
    private GeminiGateway geminiGateway;

    @Mock
    private PlatformTransactionManager transactionManager;

    private RunPortraitsStepUseCase useCase;

    @BeforeEach
    void setUp() {
        TransactionStatus status = new DefaultTransactionStatus(null, true, true, false, true, null);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(status);
        useCase = new RunPortraitsStepUseCase(
                projectRepository, characterRepository, imageStorage, pipelineLock, geminiGateway, transactionManager);
    }

    private static Project onPortraitsStep() {
        Project project = new Project(OWNER_ID, "Title", "/book.txt");
        ReflectionTestUtils.setField(project, "id", 1L);
        project.recordStyle("watercolor storybook");
        project.startStep();
        project.completeStep();
        project.advanceToNextStep(); // STYLE -> CHARACTERS
        project.startStep();
        project.completeStep();
        project.advanceToNextStep(); // CHARACTERS -> PORTRAITS, IDLE
        return project;
    }

    private static Project asIfJustAcquired(Project project) {
        Project locked = new Project(project.getUserId(), project.getTitle(), project.getBookTextPath());
        ReflectionTestUtils.setField(locked, "id", project.getId());
        locked.recordStyle(project.getStyle());
        locked.startStep();
        locked.completeStep();
        locked.advanceToNextStep();
        locked.startStep();
        locked.completeStep();
        locked.advanceToNextStep();
        locked.startStep(); // PORTRAITS IDLE -> RUNNING
        return locked;
    }

    private static Character character(long id, String name) {
        Character character = new Character(1L, name, "a description");
        ReflectionTestUtils.setField(character, "id", id);
        return character;
    }

    @Test
    void happyPathGeneratesAPortraitPerCharacterAndAdvancesToChapters() {
        Project project = onPortraitsStep();
        Project lockedProject = asIfJustAcquired(project);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project), Optional.of(lockedProject));
        when(pipelineLock.tryAcquire(1L, "PORTRAITS")).thenReturn(true);
        List<Character> characters = List.of(character(10L, "Alice"), character(11L, "Hatter"));
        when(characterRepository.findByProjectId(1L)).thenReturn(characters);
        when(geminiGateway.generatePortraits(any())).thenReturn(new GeminiGateway.PortraitsGenerationResult(
                List.of(new GeminiGateway.PortraitResult(10L, new byte[]{1}, "image/png"),
                        new GeminiGateway.PortraitResult(11L, new byte[]{2}, "image/png")),
                "img-interaction-2"));
        when(imageStorage.save(any(), any())).thenReturn("/images/a.png", "/images/b.png");
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PipelineStepResult result = useCase.execute(1L, OWNER_ID);

        assertThat(result.inProgress()).isFalse();
        assertThat(result.project().getCurrentStep()).isEqualTo(PipelineStep.CHAPTERS);
        assertThat(characters.get(0).getPortraitImagePath()).isEqualTo("/images/a.png");
        assertThat(characters.get(1).getPortraitImagePath()).isEqualTo("/images/b.png");
        verify(characterRepository).saveAll(characters);
    }

    @Test
    void geminiFailureIsClassifiedAndPersistedWithoutSavingPartialPortraits() {
        Project project = onPortraitsStep();
        Project lockedProject = asIfJustAcquired(project);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project), Optional.of(lockedProject));
        when(pipelineLock.tryAcquire(1L, "PORTRAITS")).thenReturn(true);
        when(characterRepository.findByProjectId(1L)).thenReturn(List.of(character(10L, "Alice")));
        when(geminiGateway.generatePortraits(any())).thenThrow(
                new GeminiGenerationException("UPSTREAM_ERROR", "The AI service failed.", true));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> useCase.execute(1L, OWNER_ID))
                .isInstanceOf(GeminiGenerationException.class);
        verify(characterRepository, never()).saveAll(any());
        assertThat(lockedProject.getStepState().name()).isEqualTo("FAILED");
    }

    @Test
    void imageStorageFailureAfterAGeminiSuccessIsStillClassifiedAndPersisted() {
        // Reproduces the same bug class caught in #14/#15: a failure AFTER Gemini
        // already succeeded (here, a local disk write) must still leave the step
        // FAILED with a persisted message, not stuck RUNNING with an uncaught 500.
        Project project = onPortraitsStep();
        Project lockedProject = asIfJustAcquired(project);
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project), Optional.of(lockedProject));
        when(pipelineLock.tryAcquire(1L, "PORTRAITS")).thenReturn(true);
        when(characterRepository.findByProjectId(1L)).thenReturn(List.of(character(10L, "Alice")));
        when(geminiGateway.generatePortraits(any())).thenReturn(new GeminiGateway.PortraitsGenerationResult(
                List.of(new GeminiGateway.PortraitResult(10L, new byte[]{1}, "image/png")), "img-1"));
        when(imageStorage.save(any(), any())).thenThrow(new java.io.UncheckedIOException(
                new java.io.IOException("disk full")));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> useCase.execute(1L, OWNER_ID))
                .isInstanceOf(GeminiGenerationException.class);
        verify(characterRepository, never()).saveAll(any());
        assertThat(lockedProject.getStepState().name()).isEqualTo("FAILED");
    }

    @Test
    void retryAfterFailureCallsGeminiAgain() {
        Project project = onPortraitsStep();
        project.startStep();
        project.failStep("previous attempt failed");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(characterRepository.findByProjectId(1L)).thenReturn(List.of(character(10L, "Alice")));
        when(geminiGateway.generatePortraits(any())).thenReturn(new GeminiGateway.PortraitsGenerationResult(
                List.of(new GeminiGateway.PortraitResult(10L, new byte[]{1}, "image/png")), "img-1"));
        when(imageStorage.save(any(), any())).thenReturn("/images/a.png");
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PipelineStepResult result = useCase.execute(1L, OWNER_ID);

        assertThat(result.inProgress()).isFalse();
        verify(geminiGateway).generatePortraits(any());
    }

    @Test
    void secondConcurrentCallReportsInProgressWithoutCallingGemini() {
        Project project = onPortraitsStep();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(pipelineLock.tryAcquire(1L, "PORTRAITS")).thenReturn(false);

        PipelineStepResult result = useCase.execute(1L, OWNER_ID);

        assertThat(result.inProgress()).isTrue();
        verify(geminiGateway, never()).generatePortraits(any());
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
