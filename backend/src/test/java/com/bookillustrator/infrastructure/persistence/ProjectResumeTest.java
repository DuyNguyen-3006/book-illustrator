package com.bookillustrator.infrastructure.persistence;

import com.bookillustrator.domain.entity.Project;
import com.bookillustrator.domain.enums.ResumeAction;
import com.bookillustrator.infrastructure.persistence.repository.ProjectJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The literal "kill mid-step, reload, resumes correctly" scenario from #13: a row is
 * written directly (simulating a process that died mid-call, never got to update its
 * own state), then reloaded through the real JPA repository — not constructed in
 * memory — to prove resume works off what's actually in Postgres.
 */
@SpringBootTest
class ProjectResumeTest {

    @Autowired
    private ProjectJpaRepository projectJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long userId;
    private long projectId;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM projects WHERE id = ?", projectId);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
    }

    @Test
    void reloadingAStepThatDiedPastTheTtlReclaimsIt() {
        // Simulates a server that crashed 5 minutes into a step — well past the 120s TTL.
        insertProjectRow("RUNNING", OffsetDateTime.now().minusMinutes(5));

        Project reloaded = projectJpaRepository.findById(projectId).orElseThrow();

        assertThat(reloaded.resumeAction(OffsetDateTime.now())).isEqualTo(ResumeAction.RECLAIM_AND_RESTART);
    }

    @Test
    void reloadingAStepStillWithinTheTtlReportsInProgress() {
        insertProjectRow("RUNNING", OffsetDateTime.now().minusSeconds(5));

        Project reloaded = projectJpaRepository.findById(projectId).orElseThrow();

        assertThat(reloaded.resumeAction(OffsetDateTime.now())).isEqualTo(ResumeAction.IN_PROGRESS);
    }

    @Test
    void reloadingARunningStepWithNoStartedAtIsTreatedAsStale() {
        // A RUNNING row with a null step_started_at shouldn't happen through this app's
        // own writes anymore, but a raw/legacy row could have it — fail safe (reclaim)
        // rather than getting stuck reporting "in progress" forever.
        insertProjectRow("RUNNING", null);

        Project reloaded = projectJpaRepository.findById(projectId).orElseThrow();

        assertThat(reloaded.resumeAction(OffsetDateTime.now())).isEqualTo(ResumeAction.RECLAIM_AND_RESTART);
    }

    @Test
    void reloadingAnIdleStepReportsStart() {
        insertProjectRow("IDLE", null);

        Project reloaded = projectJpaRepository.findById(projectId).orElseThrow();

        assertThat(reloaded.resumeAction(OffsetDateTime.now())).isEqualTo(ResumeAction.START);
    }

    private void insertProjectRow(String stepState, OffsetDateTime stepStartedAt) {
        userId = jdbcTemplate.queryForObject(
                "INSERT INTO users (email, name) VALUES (?, ?) RETURNING id",
                Long.class, "resume-test-" + System.nanoTime() + "@test.local", "Resume Test User");
        projectId = jdbcTemplate.queryForObject("""
                INSERT INTO projects (user_id, title, book_text_path, current_step, step_state, step_started_at)
                VALUES (?, 'Resume test project', '/tmp/does-not-matter.txt', 'STYLE', ?, ?)
                RETURNING id
                """, Long.class, userId, stepState, stepStartedAt);
    }
}
