package com.bookillustrator.interfaces.rest.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Requires the docker-compose Postgres running (same pattern as AuthControllerTest) —
 * no mocks for the DB round-trip.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProjectControllerTest {

    private static final String TEST_EMAIL = "project-controller-test@test.local";
    private static final String OTHER_EMAIL = "project-controller-test-other@test.local";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        for (String email : List.of(TEST_EMAIL, OTHER_EMAIL)) {
            jdbcTemplate.update(
                    "DELETE FROM projects WHERE user_id IN (SELECT id FROM users WHERE email = ?)", email);
            jdbcTemplate.update("DELETE FROM users WHERE email = ?", email);
        }
    }

    private MockHttpSession loggedInSession() throws Exception {
        return loggedInSession(TEST_EMAIL);
    }

    private MockHttpSession loggedInSession(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/session")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"name\":\"Test User\"}"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void createsAProjectWithPastedText() throws Exception {
        MockHttpSession session = loggedInSession();

        mockMvc.perform(post("/projects")
                        .session(session)
                        .param("title", "The Wind in the Willows")
                        .param("bookText", "Once upon a time..."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.title").value("The Wind in the Willows"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void createsAProjectFromATxtUpload() throws Exception {
        MockHttpSession session = loggedInSession();
        MockMultipartFile file = new MockMultipartFile(
                "file", "book.txt", "text/plain", "Chapter one...".getBytes());

        mockMvc.perform(multipart("/projects")
                        .file(file)
                        .session(session)
                        .param("title", "Uploaded Book"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Uploaded Book"));
    }

    @Test
    void rejectsWhenNeitherPastedTextNorFileIsGiven() throws Exception {
        MockHttpSession session = loggedInSession();

        mockMvc.perform(post("/projects")
                        .session(session)
                        .param("title", "No Content"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void rejectsWhenBothPastedTextAndFileAreGiven() throws Exception {
        MockHttpSession session = loggedInSession();
        MockMultipartFile file = new MockMultipartFile("file", "book.txt", "text/plain", "text".getBytes());

        mockMvc.perform(multipart("/projects")
                        .file(file)
                        .session(session)
                        .param("title", "Both")
                        .param("bookText", "also pasted"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void rejectsANonTxtUpload() throws Exception {
        MockHttpSession session = loggedInSession();
        MockMultipartFile file = new MockMultipartFile(
                "file", "book.pdf", "application/pdf", "not text".getBytes());

        mockMvc.perform(multipart("/projects")
                        .file(file)
                        .session(session)
                        .param("title", "Bad Extension"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void rejectsABlankTitle() throws Exception {
        MockHttpSession session = loggedInSession();

        mockMvc.perform(post("/projects")
                        .session(session)
                        .param("title", "   ")
                        .param("bookText", "text"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(post("/projects")
                        .param("title", "No Session")
                        .param("bookText", "text"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void listReturnsEmptyArrayNotErrorWhenUserHasNoProjects() throws Exception {
        MockHttpSession session = loggedInSession();

        mockMvc.perform(get("/projects").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void listReturnsTheUsersProjectsNewestFirst() throws Exception {
        MockHttpSession session = loggedInSession();

        mockMvc.perform(post("/projects").session(session)
                .param("title", "First").param("bookText", "text"));
        mockMvc.perform(post("/projects").session(session)
                .param("title", "Second").param("bookText", "text"));

        mockMvc.perform(get("/projects").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].title").value("Second"))
                .andExpect(jsonPath("$.data[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.data[0].currentStep").value("STYLE"))
                .andExpect(jsonPath("$.data[0].stepState").value("IDLE"))
                .andExpect(jsonPath("$.data[1].title").value("First"));
    }

    @Test
    void listUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void detailReturnsFullProjectForTheOwner() throws Exception {
        MockHttpSession session = loggedInSession();
        mockMvc.perform(post("/projects").session(session)
                .param("title", "Detail Test").param("bookText", "Once upon a time..."));
        long projectId = jdbcTemplate.queryForObject(
                "SELECT id FROM projects WHERE title = 'Detail Test'", Long.class);

        mockMvc.perform(get("/projects/" + projectId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.title").value("Detail Test"))
                .andExpect(jsonPath("$.data.bookText").value("Once upon a time..."))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.currentStep").value("STYLE"))
                .andExpect(jsonPath("$.data.stepState").value("IDLE"))
                .andExpect(jsonPath("$.data.characters").isArray())
                .andExpect(jsonPath("$.data.characters").isEmpty())
                .andExpect(jsonPath("$.data.chapters").isArray())
                .andExpect(jsonPath("$.data.chapters").isEmpty());
    }

    @Test
    void detailReturns404ForAnotherUsersProject() throws Exception {
        MockHttpSession ownerSession = loggedInSession();
        mockMvc.perform(post("/projects").session(ownerSession)
                .param("title", "Not Yours").param("bookText", "text"));
        long projectId = jdbcTemplate.queryForObject(
                "SELECT id FROM projects WHERE title = 'Not Yours'", Long.class);

        MockHttpSession otherSession = loggedInSession(OTHER_EMAIL);
        mockMvc.perform(get("/projects/" + projectId).session(otherSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void detailReturns404ForNonexistentProject() throws Exception {
        MockHttpSession session = loggedInSession();

        mockMvc.perform(get("/projects/999999999").session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void detailUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/projects/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void detailWithNonNumericIdReturns400NotServerError() throws Exception {
        MockHttpSession session = loggedInSession();

        mockMvc.perform(get("/projects/abc").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }
}
