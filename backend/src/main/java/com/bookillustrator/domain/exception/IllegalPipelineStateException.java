package com.bookillustrator.domain.exception;

/** Thrown when a pipeline state transition isn't legal — see pipeline-rules SKILL.md §1. */
public class IllegalPipelineStateException extends DomainException {
    public IllegalPipelineStateException(String message) {
        super(message);
    }
}
