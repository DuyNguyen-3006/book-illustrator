package com.bookillustrator.infrastructure.persistence;

import com.bookillustrator.application.port.output.PipelineLock;
import com.bookillustrator.domain.entity.Project;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * A single atomic conditional UPDATE — not a held SELECT ... FOR UPDATE transaction.
 * Holding a DB transaction open across a 10-30s+ Gemini call would tie up a
 * connection-pool slot for the whole call; this acquires and releases instantly, and
 * the row itself (step_state + step_started_at) carries the lock state across the
 * actual API call. See docs/architecture.md §14. TTL is Project.LOCK_TTL (120s) — see
 * DECISIONS.md for why, and pipeline-rules SKILL.md §3 for how resume uses the same
 * value to decide a RUNNING step died mid-call.
 */
@Component
public class PostgresPipelineLock implements PipelineLock {

    private final JdbcTemplate jdbcTemplate;

    public PostgresPipelineLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean tryAcquire(long projectId, String stepId) {
        // Project.LOCK_TTL is a compile-time constant, not external input — safe to
        // format directly into the SQL text instead of binding it as a parameter
        // (avoids relying on Postgres's implicit type inference for `? || 'seconds'`).
        int updated = jdbcTemplate.update("""
                UPDATE projects
                SET step_state = 'RUNNING',
                    step_started_at = now(),
                    updated_at = now()
                WHERE id = ?
                  AND current_step = ?
                  AND (step_state = 'IDLE'
                       OR (step_state = 'RUNNING' AND step_started_at < now() - INTERVAL '%d seconds'))
                """.formatted(Project.LOCK_TTL.getSeconds()), projectId, stepId);
        return updated == 1;
    }
}
