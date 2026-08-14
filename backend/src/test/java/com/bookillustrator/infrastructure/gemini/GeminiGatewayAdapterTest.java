package com.bookillustrator.infrastructure.gemini;

import com.bookillustrator.application.port.output.GeminiGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Mock
    private GeminiClient client;

    private GeminiGatewayAdapter adapter;

    private GeminiGatewayAdapter adapter() {
        return new GeminiGatewayAdapter(client, MODEL);
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
}
