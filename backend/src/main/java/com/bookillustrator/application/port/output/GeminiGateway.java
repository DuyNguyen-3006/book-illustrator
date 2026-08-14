package com.bookillustrator.application.port.output;

import java.util.List;

/**
 * Business-level Gemini operations — pipeline use cases depend on this, never on the
 * REST client directly. See docs/architecture.md §12. generatePortrait/
 * generateChapters/generateIllustration get added as #16-18 land (one vertical slice
 * at a time).
 */
public interface GeminiGateway {

    StyleGenerationResult generateStyle(StyleGenerationRequest request);

    CharactersGenerationResult generateCharacters(CharactersGenerationRequest request);

    PortraitsGenerationResult generatePortraits(PortraitsGenerationRequest request);

    /**
     * @param bookText            full book text — only actually sent if this is the
     *                            project's first Gemini call ({@code existingBookFileUri} is null)
     * @param existingBookFileUri set once the book has already been uploaded; null on the first call
     * @param previousInteractionId chain handle from the last text call, or null on the first call
     * @param userProvidedStyle   optional — spec §4.4 step 1 accepts a user-supplied style
     */
    record StyleGenerationRequest(
            String bookText,
            String existingBookFileUri,
            String previousInteractionId,
            String userProvidedStyle) {
    }

    record StyleGenerationResult(String style, String bookFileUri, String interactionId) {
    }

    /**
     * No book text/file needed — the text chain (previousInteractionId) already carries
     * the book and the established style forward (pipeline-rules SKILL.md §5).
     */
    record CharactersGenerationRequest(String previousInteractionId) {
    }

    record CharacterDraft(String name, String prompt) {
    }

    record CharactersGenerationResult(List<CharacterDraft> characters, String interactionId) {
    }

    /**
     * The image chain is separate from the text chain and is seeded fresh with the
     * style on its first call (pipeline-rules SKILL.md §5) — not just a chain handle.
     */
    record PortraitsGenerationRequest(
            String style, List<CharacterForPortrait> characters, String previousImageInteractionId) {
    }

    record CharacterForPortrait(long characterId, String name, String prompt) {
    }

    record PortraitResult(long characterId, byte[] imageBytes, String mimeType) {
    }

    record PortraitsGenerationResult(List<PortraitResult> portraits, String interactionId) {
    }
}
