package com.bookillustrator.infrastructure.gemini;

import com.bookillustrator.application.port.output.GeminiGateway;
import com.bookillustrator.domain.exception.GeminiGenerationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiGatewayAdapterTest {

    private static final String MODEL = "gemini-3.6-flash";
    private static final String IMAGE_MODEL = "gemini-3.1-flash-lite-image";

    @Mock
    private GeminiClient client;

    private GeminiGatewayAdapter adapter;

    private GeminiGatewayAdapter adapter() {
        return new GeminiGatewayAdapter(client, MODEL, IMAGE_MODEL, new ObjectMapper());
    }

    @Test
    void firstCallForAProjectUploadsTheBookThenChainsTheStyleCall() {
        adapter = adapter();
        when(client.uploadTextFile(eq("Once upon a time..."), anyString()))
                .thenReturn(new GeminiClient.FileRef("files/book-uri", "text/plain"));
        when(client.createInteraction(eq(MODEL), any(), isNull()))
                .thenReturn(new GeminiClient.InteractionResult("interaction-1", null));
        when(client.createInteraction(eq(MODEL), any(), eq("interaction-1")))
                .thenReturn(new GeminiClient.InteractionResult("interaction-2", "Edwardian Clockwork-Pastoral"));

        GeminiGateway.StyleGenerationResult result = adapter.generateStyle(
                new GeminiGateway.StyleGenerationRequest("Once upon a time...", null, null, null));

        assertThat(result.style()).isEqualTo("Edwardian Clockwork-Pastoral");
        assertThat(result.bookFileUri()).isEqualTo("files/book-uri");
        assertThat(result.interactionId()).isEqualTo("interaction-2");
        verify(client).uploadTextFile(eq("Once upon a time..."), anyString());
    }

    @Test
    void subsequentCallReusesTheExistingFileAndChainsOffThePreviousInteraction() {
        adapter = adapter();
        when(client.createInteraction(eq(MODEL), any(), eq("previous-id")))
                .thenReturn(new GeminiClient.InteractionResult("interaction-3", "A generated style"));

        GeminiGateway.StyleGenerationResult result = adapter.generateStyle(
                new GeminiGateway.StyleGenerationRequest(
                        "Once upon a time...", "files/already-uploaded", "previous-id", null));

        assertThat(result.bookFileUri()).isEqualTo("files/already-uploaded");
        assertThat(result.style()).isEqualTo("A generated style");
        verify(client, never()).uploadTextFile(any(), any());
    }

    @Test
    void userProvidedStyleIsUsedVerbatimNotGeminisOutputText() {
        adapter = adapter();
        when(client.createInteraction(eq(MODEL), any(), eq("previous-id")))
                .thenReturn(new GeminiClient.InteractionResult("interaction-4", "ignored gemini text"));

        GeminiGateway.StyleGenerationResult result = adapter.generateStyle(
                new GeminiGateway.StyleGenerationRequest(
                        "text", "files/uri", "previous-id", "watercolor storybook"));

        assertThat(result.style()).isEqualTo("watercolor storybook");
    }

    @Test
    void styleInputMentionsTheUserProvidedStyleWhenGiven() {
        adapter = adapter();
        when(client.createInteraction(eq(MODEL), any(), eq("previous-id")))
                .thenReturn(new GeminiClient.InteractionResult("id", "text"));

        adapter.generateStyle(new GeminiGateway.StyleGenerationRequest(
                "text", "files/uri", "previous-id", "watercolor storybook"));

        ArgumentCaptor<List<Map<String, Object>>> inputCaptor = ArgumentCaptor.forClass(List.class);
        verify(client).createInteraction(eq(MODEL), inputCaptor.capture(), eq("previous-id"));
        String text = (String) inputCaptor.getValue().get(0).get("text");
        assertThat(text).contains("watercolor storybook");
    }

    @Test
    void generateCharactersParsesTheStructuredJsonReply() {
        adapter = adapter();
        when(client.createInteraction(eq(MODEL), any(), eq("previous-id"), any()))
                .thenReturn(new GeminiClient.InteractionResult("interaction-5",
                        "[{\"name\":\"Alice\",\"prompt\":\"a curious young woman\"},"
                                + "{\"name\":\"The Mad Hatter\",\"prompt\":\"an eccentric man\"}]"));

        GeminiGateway.CharactersGenerationResult result = adapter.generateCharacters(
                new GeminiGateway.CharactersGenerationRequest("previous-id"));

        assertThat(result.interactionId()).isEqualTo("interaction-5");
        assertThat(result.characters()).containsExactly(
                new GeminiGateway.CharacterDraft("Alice", "a curious young woman"),
                new GeminiGateway.CharacterDraft("The Mad Hatter", "an eccentric man"));
    }

    @Test
    void generateCharactersRejectsMoreThanTwo() {
        adapter = adapter();
        when(client.createInteraction(eq(MODEL), any(), eq("previous-id"), any()))
                .thenReturn(new GeminiClient.InteractionResult("id",
                        "[{\"name\":\"A\",\"prompt\":\"p\"},{\"name\":\"B\",\"prompt\":\"p\"},"
                                + "{\"name\":\"C\",\"prompt\":\"p\"}]"));

        assertThatThrownBy(() -> adapter.generateCharacters(
                new GeminiGateway.CharactersGenerationRequest("previous-id")))
                .isInstanceOf(GeminiGenerationException.class)
                .satisfies(e -> assertThat(((GeminiGenerationException) e).getCode()).isEqualTo("INVALID_OUTPUT"));
    }

    @Test
    void generateCharactersRejectsUnparsableOutput() {
        adapter = adapter();
        when(client.createInteraction(eq(MODEL), any(), eq("previous-id"), any()))
                .thenReturn(new GeminiClient.InteractionResult("id", "not json"));

        assertThatThrownBy(() -> adapter.generateCharacters(
                new GeminiGateway.CharactersGenerationRequest("previous-id")))
                .isInstanceOf(GeminiGenerationException.class)
                .satisfies(e -> assertThat(((GeminiGenerationException) e).getCode()).isEqualTo("INVALID_OUTPUT"));
    }

    @Test
    void generateCharactersSendsTheJsonSchemaInTheRequest() {
        adapter = adapter();
        when(client.createInteraction(eq(MODEL), any(), eq("previous-id"), any()))
                .thenReturn(new GeminiClient.InteractionResult("id", "[]"));

        adapter.generateCharacters(new GeminiGateway.CharactersGenerationRequest("previous-id"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> schemaCaptor = ArgumentCaptor.forClass(Map.class);
        verify(client).createInteraction(eq(MODEL), any(), eq("previous-id"), schemaCaptor.capture());
        assertThat(schemaCaptor.getValue()).containsEntry("type", "array");
    }

    @Test
    void generatePortraitsChainsOneCallPerCharacterAndReturnsEachImage() {
        adapter = adapter();
        byte[] image1 = {1, 2, 3};
        byte[] image2 = {4, 5, 6};
        when(client.createImageInteraction(eq(IMAGE_MODEL), any(), isNull()))
                .thenReturn(new GeminiClient.ImageInteractionResult("img-1", image1, "image/png"));
        when(client.createImageInteraction(eq(IMAGE_MODEL), any(), eq("img-1")))
                .thenReturn(new GeminiClient.ImageInteractionResult("img-2", image2, "image/png"));

        GeminiGateway.PortraitsGenerationResult result = adapter.generatePortraits(
                new GeminiGateway.PortraitsGenerationRequest(
                        "watercolor storybook",
                        List.of(new GeminiGateway.CharacterForPortrait(1L, "Alice", "a curious young woman"),
                                new GeminiGateway.CharacterForPortrait(2L, "The Hatter", "an eccentric man")),
                        null));

        assertThat(result.interactionId()).isEqualTo("img-2");
        assertThat(result.portraits()).hasSize(2);
        assertThat(result.portraits().get(0).characterId()).isEqualTo(1L);
        assertThat(result.portraits().get(0).imageBytes()).isEqualTo(image1);
        assertThat(result.portraits().get(1).characterId()).isEqualTo(2L);
        assertThat(result.portraits().get(1).imageBytes()).isEqualTo(image2);
    }

    @Test
    void generatePortraitsSeedsTheFirstCallWithStyle() {
        adapter = adapter();
        when(client.createImageInteraction(eq(IMAGE_MODEL), any(), isNull()))
                .thenReturn(new GeminiClient.ImageInteractionResult("img-1", new byte[0], "image/png"));

        adapter.generatePortraits(new GeminiGateway.PortraitsGenerationRequest(
                "watercolor storybook",
                List.of(new GeminiGateway.CharacterForPortrait(1L, "Alice", "a curious young woman")),
                null));

        ArgumentCaptor<List<Map<String, Object>>> inputCaptor = ArgumentCaptor.forClass(List.class);
        verify(client).createImageInteraction(eq(IMAGE_MODEL), inputCaptor.capture(), isNull());
        String text = (String) inputCaptor.getValue().get(0).get("text");
        assertThat(text).contains("watercolor storybook");
    }

    @Test
    void generatePortraitsContinuesAnExistingImageChain() {
        adapter = adapter();
        when(client.createImageInteraction(eq(IMAGE_MODEL), any(), eq("existing-chain-id")))
                .thenReturn(new GeminiClient.ImageInteractionResult("img-1", new byte[0], "image/png"));

        adapter.generatePortraits(new GeminiGateway.PortraitsGenerationRequest(
                "watercolor storybook",
                List.of(new GeminiGateway.CharacterForPortrait(1L, "Alice", "a curious young woman")),
                "existing-chain-id"));

        verify(client).createImageInteraction(eq(IMAGE_MODEL), any(), eq("existing-chain-id"));
    }

    @Test
    void generateChaptersParsesTheStructuredJsonReply() {
        adapter = adapter();
        when(client.createInteraction(eq(MODEL), any(), eq("previous-id"), any()))
                .thenReturn(new GeminiClient.InteractionResult("interaction-6",
                        "[{\"name\":\"The Tea Party\",\"prompt\":\"a chaotic tea party scene\","
                                + "\"characters\":[\"Alice\",\"The Mad Hatter\"]}]"));

        GeminiGateway.ChaptersGenerationResult result = adapter.generateChapters(
                new GeminiGateway.ChaptersGenerationRequest("previous-id"));

        assertThat(result.interactionId()).isEqualTo("interaction-6");
        assertThat(result.chapters()).containsExactly(
                new GeminiGateway.ChapterDraft("The Tea Party", "a chaotic tea party scene",
                        List.of("Alice", "The Mad Hatter")));
    }

    @Test
    void generateChaptersRejectsMoreThanOne() {
        adapter = adapter();
        when(client.createInteraction(eq(MODEL), any(), eq("previous-id"), any()))
                .thenReturn(new GeminiClient.InteractionResult("id",
                        "[{\"name\":\"A\",\"prompt\":\"p\",\"characters\":[]},"
                                + "{\"name\":\"B\",\"prompt\":\"p\",\"characters\":[]}]"));

        assertThatThrownBy(() -> adapter.generateChapters(
                new GeminiGateway.ChaptersGenerationRequest("previous-id")))
                .isInstanceOf(GeminiGenerationException.class)
                .satisfies(e -> assertThat(((GeminiGenerationException) e).getCode()).isEqualTo("INVALID_OUTPUT"));
    }

    @Test
    void generateChaptersRejectsUnparsableOutput() {
        adapter = adapter();
        when(client.createInteraction(eq(MODEL), any(), eq("previous-id"), any()))
                .thenReturn(new GeminiClient.InteractionResult("id", "not json"));

        assertThatThrownBy(() -> adapter.generateChapters(
                new GeminiGateway.ChaptersGenerationRequest("previous-id")))
                .isInstanceOf(GeminiGenerationException.class)
                .satisfies(e -> assertThat(((GeminiGenerationException) e).getCode()).isEqualTo("INVALID_OUTPUT"));
    }
}
