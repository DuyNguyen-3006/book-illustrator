package com.bookillustrator.domain.exception;

/**
 * A Gemini call failed. Carries an already-classified, user-safe code/message pair
 * (never the raw provider error — backend-rules SKILL.md §2) plus whether the user may
 * retry (they always may, via the UI — this only affects messaging/status).
 */
public class GeminiGenerationException extends DomainException {

    private final String code;
    private final boolean retriable;

    public GeminiGenerationException(String code, String message, boolean retriable) {
        super(message);
        this.code = code;
        this.retriable = retriable;
    }

    public String getCode() {
        return code;
    }

    public boolean isRetriable() {
        return retriable;
    }
}
