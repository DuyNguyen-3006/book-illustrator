package com.bookillustrator.application.port;

/**
 * Per-(project, current step) lock, acquired before any Gemini call.
 * See .claude/skills/pipeline-rules/SKILL.md §2.
 */
public interface PipelineLock {


    boolean tryAcquire(long projectId, String stepId);
}
