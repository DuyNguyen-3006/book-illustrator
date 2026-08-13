package com.bookillustrator.infrastructure.persistence;

import com.bookillustrator.application.port.PipelineLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Backend-rules §3: lock behaviour must be tested against real storage semantics, not
 * a mock. Requires the docker-compose Postgres to already be running (start.sh) — no
 * Testcontainers, per the user's explicit choice, logged in DECISIONS.md.
 */
@SpringBootTest
class PostgresPipelineLockTest {

    @Autowired
    private PipelineLock pipelineLock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long userId;
    private long projectId;

    @Test
    void exactlyOneOfTwoConcurrentAttemptsAcquiresTheLock() throws InterruptedException {
        setUpTestProject();
        try {
            int threadCount = 2;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch ready = new CountDownLatch(threadCount);
            CountDownLatch go = new CountDownLatch(1);
            AtomicInteger successCount = new AtomicInteger();

            for (int i = 0; i < threadCount; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    awaitUninterruptibly(go);
                    if (pipelineLock.tryAcquire(projectId, "style")) {
                        successCount.incrementAndGet();
                    }
                });
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

            assertThat(successCount.get()).isEqualTo(1);
            assertThat(currentStepState()).isEqualTo("locked");
        } finally {
            tearDownTestProject();
        }
    }

    @Test
    void secondAttemptFailsWhileLockIsHeldAndNotExpired() {
        setUpTestProject();
        try {
            assertThat(pipelineLock.tryAcquire(projectId, "style")).isTrue();
            assertThat(pipelineLock.tryAcquire(projectId, "style")).isFalse();
        } finally {
            tearDownTestProject();
        }
    }

    @Test
    void wrongStepIdDoesNotAcquire() {
        setUpTestProject();
        try {
            assertThat(pipelineLock.tryAcquire(projectId, "characters")).isFalse();
            assertThat(currentStepState()).isEqualTo("pending");
        } finally {
            tearDownTestProject();
        }
    }

    private String currentStepState() {
        return jdbcTemplate.queryForObject(
                "SELECT step_state FROM projects WHERE id = ?", String.class, projectId);
    }

    private void setUpTestProject() {
        userId = jdbcTemplate.queryForObject(
                "INSERT INTO users (email, name) VALUES (?, ?) RETURNING id",
                Long.class, "lock-test-" + System.nanoTime() + "@test.local", "Lock Test User");
        projectId = jdbcTemplate.queryForObject("""
                INSERT INTO projects (user_id, title, book_text_path, current_step, step_state)
                VALUES (?, 'Lock test project', '/tmp/does-not-matter.txt', 'style', 'pending')
                RETURNING id
                """, Long.class, userId);
    }

    private void tearDownTestProject() {
        jdbcTemplate.update("DELETE FROM projects WHERE id = ?", projectId);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
