package com.bookillustrator.infrastructure.gemini;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single attempt per call, no retry loop (CLAUDE.md §2.2): a bounded timeout is set so
 * a hung call fails instead of blocking forever, but a failure is never retried here —
 * the caller persists it and the user decides.
 *
 * REST shape confirmed 2026-08-14 against a real GET /v1beta/models call (200, both
 * gemini-3.6-flash and gemini-3.1-flash-lite-image present) plus
 * https://ai.google.dev/api/interactions-api, /gemini-api/docs/files, and
 * /gemini-api/docs/file-input-methods — not guessed. See issue #14.
 */
@Component
public class RestGeminiClient implements GeminiClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com";
    private static final int TIMEOUT_MS = 60_000;

    private final RestClient restClient;

    public RestGeminiClient(@Value("${gemini.api-key}") String apiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT_MS);
        requestFactory.setReadTimeout(TIMEOUT_MS);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
    }

    /** Resumable upload (start, then upload+finalize) — see /gemini-api/docs/files. */
    @Override
    public FileRef uploadTextFile(String content, String displayName) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        ResponseEntity<Void> started = restClient.post()
                .uri(BASE_URL + "/upload/v1beta/files")
                .header("X-Goog-Upload-Protocol", "resumable")
                .header("X-Goog-Upload-Command", "start")
                .header("X-Goog-Upload-Header-Content-Length", String.valueOf(bytes.length))
                .header("X-Goog-Upload-Header-Content-Type", "text/plain")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("file", Map.of("display_name", displayName)))
                .retrieve()
                .toBodilessEntity();

        String uploadUrl = started.getHeaders().getFirst("X-Goog-Upload-URL");
        if (uploadUrl == null) {
            throw new IllegalStateException("Gemini file upload did not return an upload URL");
        }

        Map<String, Object> response = restClient.post()
                .uri(uploadUrl)
                .header("X-Goog-Upload-Offset", "0")
                .header("X-Goog-Upload-Command", "upload, finalize")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });

        @SuppressWarnings("unchecked")
        Map<String, Object> file = (Map<String, Object>) response.get("file");
        return new FileRef((String) file.get("uri"), (String) file.get("mimeType"));
    }

    /**
     * POST /v1beta/interactions — see https://ai.google.dev/api/interactions-api.
     *
     * Confirmed 2026-08-14 with a real call (see issue #14): unlike the Files API
     * (camelCase), this response is snake_case, and there is no top-level
     * {@code output_text} field — the model's reply is the {@code content[0].text} of
     * whichever entry in {@code steps} has {@code type == "model_output"} (other steps,
     * e.g. {@code type == "thought"}, are not the answer). When a schema is supplied,
     * that same text field holds a JSON string instead of prose.
     *
     * {@code response_format} shape ({@code {type, mime_type, schema}}) confirmed live
     * 2026-08-14 (issue #15) — a real structured-output call for the Characters step
     * returned a well-formed JSON array matching the requested schema.
     */
    @Override
    public InteractionResult createInteraction(
            String model, List<Map<String, Object>> input, String previousInteractionId,
            Map<String, Object> responseSchema) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", input);
        if (previousInteractionId != null) {
            body.put("previous_interaction_id", previousInteractionId);
        }
        if (responseSchema != null) {
            body.put("response_format", Map.of(
                    "type", "text",
                    "mime_type", "application/json",
                    "schema", responseSchema));
        }

        Map<String, Object> response = restClient.post()
                .uri(BASE_URL + "/v1beta/interactions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });

        return new InteractionResult((String) response.get("id"), extractOutputText(response));
    }

    @SuppressWarnings("unchecked")
    private static String extractOutputText(Map<String, Object> response) {
        List<Map<String, Object>> steps = (List<Map<String, Object>>) response.get("steps");
        if (steps == null) {
            return null;
        }
        for (Map<String, Object> step : steps) {
            if (!"model_output".equals(step.get("type"))) {
                continue;
            }
            List<Map<String, Object>> content = (List<Map<String, Object>>) step.get("content");
            if (content != null && !content.isEmpty()) {
                return (String) content.get(0).get("text");
            }
        }
        return null;
    }
}
