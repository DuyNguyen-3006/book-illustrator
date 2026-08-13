package com.bookillustrator.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Requires the docker-compose Postgres running (same pattern as
 * PostgresPipelineLockTest) — no mocks for the DB round-trip.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    private static final String TEST_EMAIL = "auth-controller-test@test.local";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", TEST_EMAIL);
    }

    @Test
    void firstLoginCreatesAUser() throws Exception {
        mockMvc.perform(post("/session")
                        .contentType("application/json")
                        .content("{\"email\":\"" + TEST_EMAIL + "\",\"name\":\"Test User\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.data.name").value("Test User"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void secondLoginWithSameEmailReturnsTheSameUserIdNoDuplicate() throws Exception {
        MvcResult first = mockMvc.perform(post("/session")
                        .contentType("application/json")
                        .content("{\"email\":\"" + TEST_EMAIL + "\",\"name\":\"Test User\"}"))
                .andExpect(status().isOk())
                .andReturn();

        mockMvc.perform(post("/session")
                        .contentType("application/json")
                        .content("{\"email\":\"" + TEST_EMAIL + "\",\"name\":\"Different Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Test User")); // original name kept

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE email = ?", Integer.class, TEST_EMAIL);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void twoSimultaneousFirstLoginsWithTheSameNewEmailBothSucceedWithoutDuplicating() throws Exception {
        int threadCount = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger okCount = new AtomicInteger();
        AtomicInteger errorCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                try {
                    int status = mockMvc.perform(post("/session")
                                    .contentType("application/json")
                                    .content("{\"email\":\"" + TEST_EMAIL + "\",\"name\":\"Racer\"}"))
                            .andReturn().getResponse().getStatus();
                    if (status == 200) {
                        okCount.incrementAndGet();
                    } else {
                        errorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(errorCount.get()).isZero();
        assertThat(okCount.get()).isEqualTo(threadCount);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE email = ?", Integer.class, TEST_EMAIL);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void invalidEmailReturns400WithInvalidInputCode() throws Exception {
        mockMvc.perform(post("/session")
                        .contentType("application/json")
                        .content("{\"email\":\"not-an-email\",\"name\":\"Test User\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void emailMissingDomainDotIsRejected() throws Exception {
        // Would have passed the old ".contains(\"@\")" check — proves the tighter regex.
        mockMvc.perform(post("/session")
                        .contentType("application/json")
                        .content("{\"email\":\"a@b\",\"name\":\"Test User\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void emailCaseIsNormalized() throws Exception {
        mockMvc.perform(post("/session")
                        .contentType("application/json")
                        .content("{\"email\":\"" + TEST_EMAIL.toUpperCase() + "\",\"name\":\"Test User\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/session")
                        .contentType("application/json")
                        .content("{\"email\":\"" + TEST_EMAIL + "\",\"name\":\"Test User\"}"))
                .andExpect(status().isOk());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE email = ?", Integer.class, TEST_EMAIL);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void logoutInvalidatesTheSessionAndExpiresTheCookie() throws Exception {
        mockMvc.perform(delete("/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(result -> {
                    String setCookie = result.getResponse().getHeader("Set-Cookie");
                    assertThat(setCookie).isNotNull().contains("JSESSIONID").contains("Max-Age=0");
                });
    }
}
