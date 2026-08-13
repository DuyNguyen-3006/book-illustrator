package com.bookillustrator.infrastructure.persistence;

import com.bookillustrator.application.port.PipelineLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * A single atomic conditional UPDATE — not a held SELECT ... FOR UPDATE transaction.
 * Holding a DB transaction open across a 10-30s+ Gemini call would tie up a
 * connection-pool slot for the whole call; this acquires and releases instantly, and
 * the row itself (step_state) carries the lock state across the actual API call.
 * TTL is 120s — see DECISIONS.md for why.
 */
@Component
public class PostgresPipelineLock implements PipelineLock {

    private static final int LOCK_TTL_SECONDS = 120;

    private final JdbcTemplate jdbcTemplate;

    public PostgresPipelineLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean tryAcquire(long projectId, String stepId) {
        // LOCK_TTL_SECONDS is a compile-time constant, not external input — safe to
        // format directly into the SQL text instead of binding it as a parameter
        // (avoids relying on Postgres's implicit type inference for `? || 'seconds'`).
        int updated = jdbcTemplate.update("""
                UPDATE projects
                SET step_state = 'locked',
                    lock_expires_at = now() + INTERVAL '%d seconds',
                    updated_at = now()
                WHERE id = ?
                  AND current_step = ?
                  AND (step_state = 'pending'
                       OR (step_state IN ('locked', 'calling') AND lock_expires_at < now()))
                """.formatted(LOCK_TTL_SECONDS), projectId, stepId);
        return updated == 1;
    }
}
