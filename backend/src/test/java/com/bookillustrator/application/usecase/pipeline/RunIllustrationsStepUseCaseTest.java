package com.bookillustrator.application.usecase.pipeline;

import com.bookillustrator.application.port.output.ChapterRepository;
import com.bookillustrator.application.port.output.CharacterRepository;
import com.bookillustrator.application.port.output.GeminiGateway;
import com.bookillustrator.application.port.output.ImageStorage;
import com.bookillustrator.application.port.output.PipelineLock;
import com.bookillustrator.application.port.output.ProjectRepository;
import com.bookillustrator.domain.entity.Chapter;
import com.bookillustrator.domain.entity.Character;
import com.bookillustrator.domain.entity.Project;
import com.bookillustrator.domain.enums.ProjectStatus;
import com.bookillustrator.domain.exception.GeminiGenerationException;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;

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
class RunIllustrationsStepUseCaseTest {

    private static final long OWNER_ID = 1L;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ChapterRepository chapterRepository;

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

    private RunIllustrationsStepUseCase useCase;

    @BeforeEach
    void setUp() {
        TransactionStatus status = new DefaultTransactionStatus(null, true, true, false, true, null);
        lenient().when(transactionManager.getTransaction(any())).thenReturn(status);
        useCase = new RunIllustrationsStepUseCase(projectRepository, chapterRepository, characterRepository,
                imageStorage, pipelineLock, geminiGateway, transactionManager);
    }

    private static Project onIllustrationsStep() {
        Project project = new Project(OWNER_ID, "Title", "/book.txt");
        ReflectionTestUtils.setField(project, "id", 1L);
        for (int i = 0; i < 4; i++) { // STYLE -> CHARACTERS -> PORTRAITS -> CHAPTERS -> ILLUSTRATIONS
            project.startStep();
            project.completeStep();
            project.advanceToNextStep();
        }
        return project;
    }

    private static Project asIfJustAcquired() {
        Project locked = onIllustrationsStep();
        locked.startStep();
        return locked;
    }

    private static Chapter chapter(long id, List<Long> characterIds) {
        Chapter chapter = new Chapter(1L, "Opening Scene", "The mole meets the rat by the river", characterIds);
        ReflectionTestUtils.setField(chapter, "id", id);
        return chapter;
    }

    private static Character characterWithPortrait(long id, String name, String portraitPath) {
        Character character = new Character(1L, name, "prompt for " + name);
        ReflectionTestUtils.setField(character, "id", id);
        character.recordPortrait(portraitPath);
        return character;
    }

    @Test
    void generatesTheIllustrationAndCompletesTheProject() {
        Project project = onIllustrationsStep();
        Project locked = asIfJustAcquired();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project), Optional.of(locked));
        when(pipelineLock.tryAcquire(1L, "ILLUSTRATIONS")).thenReturn(true);
        Chapter chapter = chapter(7L, List.of(10L));
        when(chapterRepository.findByProjectId(1L)).thenReturn(List.of(chapter));
        when(characterRepository.findByProjectId(1L))
                .thenReturn(List.of(characterWithPortrait(10L, "Mole", "/data/images/mole.jpg")));
        when(imageStorage.read("/data/images/mole.jpg"))
                .thenReturn(Optional.of(new ImageStorage.StoredImage(new byte[]{1, 2}, "image/jpeg")));
        when(geminiGateway.generateIllustrations(any())).thenReturn(
                new GeminiGateway.IllustrationsGenerationResult(
                        List.of(new GeminiGateway.IllustrationResult(7L, new byte[]{9}, "image/jpeg"))));
        when(imageStorage.save(any(), any())).thenReturn("/data/images/chapter7.jpg");
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PipelineStepResult result = useCase.execute(1L, OWNER_ID);

        assertThat(result.inProgress()).isFalse();
        // ILLUSTRATIONS is the last step: advancing marks the whole project complete.
        assertThat(result.project().getStatus()).isEqualTo(ProjectStatus.COMPLETED);
        assertThat(chapter.getIllustrationImagePath()).isEqualTo("/data/images/chapter7.jpg");
        verify(chapterRepository).saveAll(List.of(chapter));
    }

    @Test
    void sendsOnlyThePortraitsThatChapterActuallyReferences() {
        Project project = onIllustrationsStep();
        Project locked = asIfJustAcquired();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project), Optional.of(locked));
        when(pipelineLock.tryAcquire(1L, "ILLUSTRATIONS")).thenReturn(true);
        when(chapterRepository.findByProjectId(1L)).thenReturn(List.of(chapter(7L, List.of(10L))));
        when(characterRepository.findByProjectId(1L)).thenReturn(List.of(
                characterWithPortrait(10L, "Mole", "/data/images/mole.jpg"),
                characterWithPortrait(11L, "Rat", "/data/images/rat.jpg")));
        when(imageStorage.read("/data/images/mole.jpg"))
                .thenReturn(Optional.of(new ImageStorage.StoredImage(new byte[]{1}, "image/jpeg")));
        when(geminiGateway.generateIllustrations(any())).thenReturn(
                new GeminiGateway.IllustrationsGenerationResult(
                        List.of(new GeminiGateway.IllustrationResult(7L, new byte[]{9}, "image/jpeg"))));
        when(imageStorage.save(any(), any())).thenReturn("/data/images/chapter7.jpg");
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(1L, OWNER_ID);

        ArgumentCaptor<GeminiGateway.IllustrationsGenerationRequest> captor =
                ArgumentCaptor.forClass(GeminiGateway.IllustrationsGenerationRequest.class);
        verify(geminiGateway).generateIllustrations(captor.capture());
        List<GeminiGateway.ChapterForIllustration> chapters = captor.getValue().chapters();
        assertThat(chapters).hasSize(1);
        assertThat(chapters.get(0).characterPortraits()).hasSize(1);
        assertThat(chapters.get(0).characterPortraits().get(0).name()).isEqualTo("Mole");
    }

    @Test
    void aFailedCallLeavesTheStepFailedWithAPersistedMessage() {
        Project project = onIllustrationsStep();
        Project locked = asIfJustAcquired();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project), Optional.of(locked));
        when(pipelineLock.tryAcquire(1L, "ILLUSTRATIONS")).thenReturn(true);
        when(chapterRepository.findByProjectId(1L)).thenReturn(List.of(chapter(7L, List.of())));
        when(characterRepository.findByProjectId(1L)).thenReturn(List.of());
        when(geminiGateway.generateIllustrations(any()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", null, null, null));
        when(projectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> useCase.execute(1L, OWNER_ID))
                .isInstanceOf(GeminiGenerationException.class)
                .satisfies(e -> assertThat(((GeminiGenerationException) e).getCode()).isEqualTo("RATE_LIMITED"));

        assertThat(locked.getStepState().name()).isEqualTo("FAILED");
        assertThat(locked.getLastError()).isNotNull();
        verify(chapterRepository, never()).saveAll(any());
    }

    @Test
    void aSecondCallerWhileTheStepRunsIsToldItIsInProgressRatherThanCallingAgain() {
        Project project = onIllustrationsStep();
        project.startStep(); // already RUNNING, fresh timestamp
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));

        PipelineStepResult result = useCase.execute(1L, OWNER_ID);

        assertThat(result.inProgress()).isTrue();
        verify(geminiGateway, never()).generateIllustrations(any());
    }
}
