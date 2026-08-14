package com.bookillustrator.infrastructure.gemini;

import java.util.List;
import java.util.Map;

/**
 * Raw REST calls to the Gemini API — an interface (not a concrete class) so
 * GeminiGatewayAdapter can be unit-tested against a fake/mock instead of hitting real
 * Gemini (backend-rules SKILL.md §3: "no real Gemini calls in the test suite, ever").
 * See {@link RestGeminiClient} for the actual implementation.
 */
public interface GeminiClient {

    FileRef uploadTextFile(String content, String displayName);

    /** Plain-text reply — no response schema. */
    default InteractionResult createInteraction(
            String model, List<Map<String, Object>> input, String previousInteractionId) {
        return createInteraction(model, input, previousInteractionId, null);
    }

    /**
     * @param responseSchema JSON Schema the reply must comply with (structured output),
     *                       or null for a plain-text reply.
     */
    InteractionResult createInteraction(
            String model, List<Map<String, Object>> input, String previousInteractionId,
            Map<String, Object> responseSchema);

    ImageInteractionResult createImageInteraction(
            String model, List<Map<String, Object>> input, String previousInteractionId);

    record FileRef(String uri, String mimeType) {
    }

    record InteractionResult(String id, String outputText) {
    }

    record ImageInteractionResult(String id, byte[] imageBytes, String mimeType) {
    }
}
