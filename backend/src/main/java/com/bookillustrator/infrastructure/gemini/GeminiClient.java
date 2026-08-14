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

    InteractionResult createInteraction(String model, List<Map<String, Object>> input, String previousInteractionId);

    record FileRef(String uri, String mimeType) {
    }

    record InteractionResult(String id, String outputText) {
    }
}
