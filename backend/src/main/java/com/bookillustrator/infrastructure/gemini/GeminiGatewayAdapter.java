package com.bookillustrator.infrastructure.gemini;

import com.bookillustrator.application.port.output.GeminiGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Maps business-level Gemini operations onto GeminiClient calls, replicating what
 * Book_illustration.ipynb does (confirmed by a real run — see
 * .claude/skills/pipeline-rules/SKILL.md §5): upload the book once, then chain every
 * text call off previous_interaction_id instead of resending it.
 */
@Component
public class GeminiGatewayAdapter implements GeminiGateway {

    private final GeminiClient client;
    private final String textModel;

    public GeminiGatewayAdapter(GeminiClient client, @Value("${gemini.text-model}") String textModel) {
        this.client = client;
        this.textModel = textModel;
    }

    @Override
    public StyleGenerationResult generateStyle(StyleGenerationRequest request) {
        String bookFileUri = request.existingBookFileUri();
        String previousInteractionId = request.previousInteractionId();

        if (bookFileUri == null) {
            GeminiClient.FileRef file = client.uploadTextFile(request.bookText(), "book");
            bookFileUri = file.uri();

            GeminiClient.InteractionResult bookInteraction = client.createInteraction(
                    textModel,
                    List.of(
                            Map.of("type", "text", "text",
                                    "Here's a book, to illustrate using Nano Banana. Don't say "
                                            + "anything for now, instructions will follow."),
                            Map.of("type", "document", "uri", bookFileUri, "mime_type", file.mimeType())),
                    null);
            previousInteractionId = bookInteraction.id();
        }

        boolean userSuppliedStyle = request.userProvidedStyle() != null && !request.userProvidedStyle().isBlank();
        String styleInput = userSuppliedStyle
                ? "The art style will be: \"" + request.userProvidedStyle() + "\". Keep that in mind "
                        + "when generating future prompts. Keep quiet for now, instructions will follow."
                : "Can you define an art style that would fit the story but with a twist? Just give us "
                        + "the prompt for the art style that will be added to future prompts.";

        GeminiClient.InteractionResult styleInteraction = client.createInteraction(
                textModel, List.of(Map.of("type", "text", "text", styleInput)), previousInteractionId);

        String style = userSuppliedStyle ? request.userProvidedStyle() : styleInteraction.outputText();

        return new StyleGenerationResult(style, bookFileUri, styleInteraction.id());
    }
}
