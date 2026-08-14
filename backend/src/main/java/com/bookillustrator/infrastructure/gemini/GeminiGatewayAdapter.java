package com.bookillustrator.infrastructure.gemini;

import com.bookillustrator.application.port.output.GeminiGateway;
import com.bookillustrator.domain.exception.GeminiGenerationException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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

    private static final int MAX_CHARACTERS = 2;

    private static final Map<String, Object> CHARACTERS_SCHEMA = Map.of(
            "type", "array",
            "items", Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "name", Map.of("type", "string"),
                            "prompt", Map.of("type", "string")),
                    "required", List.of("name", "prompt")));

    private final GeminiClient client;
    private final String textModel;
    private final String imageModel;
    private final ObjectMapper objectMapper;

    public GeminiGatewayAdapter(
            GeminiClient client,
            @Value("${gemini.text-model}") String textModel,
            @Value("${gemini.image-model}") String imageModel,
            ObjectMapper objectMapper) {
        this.client = client;
        this.textModel = textModel;
        this.imageModel = imageModel;
        this.objectMapper = objectMapper;
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

    @Override
    public CharactersGenerationResult generateCharacters(CharactersGenerationRequest request) {
        GeminiClient.InteractionResult interaction = client.createInteraction(
                textModel,
                List.of(Map.of("type", "text", "text",
                        "Now identify the main adult characters in the story — at most "
                                + MAX_CHARACTERS + " — each with a short image-generation prompt "
                                + "for their portrait, consistent with the art style established above. "
                                + "The story may include non-adult or non-human characters; only "
                                + "include adult characters.")),
                request.previousInteractionId(),
                CHARACTERS_SCHEMA);

        List<CharacterDraft> characters = parseCharacters(interaction.outputText());
        if (characters.size() > MAX_CHARACTERS) {
            throw new GeminiGenerationException(
                    "INVALID_OUTPUT", "Gemini returned more than " + MAX_CHARACTERS + " characters.", true);
        }

        return new CharactersGenerationResult(characters, interaction.id());
    }

    @Override
    public PortraitsGenerationResult generatePortraits(PortraitsGenerationRequest request) {
        String previousInteractionId = request.previousImageInteractionId();
        List<PortraitResult> portraits = new ArrayList<>();

        boolean first = true;
        for (CharacterForPortrait character : request.characters()) {
            String prompt = first
                    ? "Art style: \"" + request.style() + "\". Generate a portrait for this character: "
                            + character.name() + " — " + character.prompt() + ". No text, no watermarks, "
                            + "no signatures, portrait only, consistent with the established art style."
                    : "Generate a portrait for this character: " + character.name() + " — "
                            + character.prompt() + ", consistent with the established art style.";
            first = false;

            GeminiClient.ImageInteractionResult interaction = client.createImageInteraction(
                    imageModel, List.of(Map.of("type", "text", "text", prompt)), previousInteractionId);
            previousInteractionId = interaction.id();

            portraits.add(new PortraitResult(character.characterId(), interaction.imageBytes(), interaction.mimeType()));
        }

        return new PortraitsGenerationResult(portraits, previousInteractionId);
    }

    private List<CharacterDraft> parseCharacters(String json) {
        try {
            List<CharacterJson> parsed = objectMapper.readValue(json, new TypeReference<List<CharacterJson>>() {
            });
            return parsed.stream().map(c -> new CharacterDraft(c.name(), c.prompt())).toList();
        } catch (Exception e) {
            throw new GeminiGenerationException(
                    "INVALID_OUTPUT", "Gemini's character list could not be parsed.", true);
        }
    }

    /** Jackson-mapped shape of one item in the structured-output array — kept out of the port. */
    private record CharacterJson(@JsonProperty("name") String name, @JsonProperty("prompt") String prompt) {
    }
}
