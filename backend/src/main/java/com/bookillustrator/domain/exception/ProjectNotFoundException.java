package com.bookillustrator.domain.exception;

/**
 * Thrown both when a project truly doesn't exist AND when it exists but belongs to a
 * different user — same exception, same 404, so a request can't distinguish "not yours"
 * from "doesn't exist" and probe for valid IDs.
 */
public class ProjectNotFoundException extends DomainException {
    public ProjectNotFoundException(long projectId) {
        super("project " + projectId + " not found");
    }
}
