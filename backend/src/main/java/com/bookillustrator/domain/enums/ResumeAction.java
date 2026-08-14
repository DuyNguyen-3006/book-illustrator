package com.bookillustrator.domain.enums;

/** What a resumed session should do next for the current step. See pipeline-rules SKILL.md §3. */
public enum ResumeAction {
    /** step_state COMPLETED — move on to the next pipeline step. */
    ADVANCE,
    /** step_state IDLE — the step hasn't started; it may be started. */
    START,
    /** step_state RUNNING, still within the TTL — do nothing, report "in progress", poll. */
    IN_PROGRESS,
    /** step_state RUNNING, past the TTL — the previous attempt died; reclaim and re-run. */
    RECLAIM_AND_RESTART,
    /** step_state FAILED — surface the error; the user decides whether to retry. */
    SURFACE_ERROR
}
