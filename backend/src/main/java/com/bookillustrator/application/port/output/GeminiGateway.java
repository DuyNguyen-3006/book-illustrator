package com.bookillustrator.application.port.output;

/**
 * Business-level Gemini operations — pipeline use cases depend on this, never on the
 * REST client directly. See docs/architecture.md §12. Only generateStyle exists so
 * far (one vertical slice at a time, #19); generateCharacters/generatePortrait/
 * generateChapters/generateIllustration get added as #15-18 land.
 */
public interface GeminiGateway {

    StyleGenerationResult generateStyle(StyleGenerationRequest request);

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
}
