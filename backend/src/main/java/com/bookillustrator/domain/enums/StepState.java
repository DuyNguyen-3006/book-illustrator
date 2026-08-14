package com.bookillustrator.domain.enums;

/** Execution state of the current step. See docs/architecture.md §4 and §14. */
public enum StepState {
    IDLE,
    RUNNING,
    FAILED,
    COMPLETED
}
