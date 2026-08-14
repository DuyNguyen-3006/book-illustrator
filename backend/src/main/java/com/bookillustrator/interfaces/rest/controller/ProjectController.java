package com.bookillustrator.interfaces.rest.controller;

import com.bookillustrator.application.usecase.project.CreateProjectUseCase;
import com.bookillustrator.interfaces.rest.response.ApiResponse;
import com.bookillustrator.interfaces.rest.response.ProjectResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/projects")
public class ProjectController {

    private static final String SESSION_USER_ID = "userId";

    private final CreateProjectUseCase createProjectUseCase;

    public ProjectController(CreateProjectUseCase createProjectUseCase) {
        this.createProjectUseCase = createProjectUseCase;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> create(
            @RequestParam("title") String title,
            @RequestParam(value = "bookText", required = false) String bookText,
            @RequestParam(value = "file", required = false) MultipartFile file,
            HttpSession session) {

        Long userId = (Long) session.getAttribute(SESSION_USER_ID);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(
                    new ApiResponse.ApiError("UNAUTHENTICATED", "Log in first.", null, false)));
        }

        String resolvedText;
        try {
            resolvedText = resolveBookText(bookText, file);
        } catch (InputResolutionException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    new ApiResponse.ApiError("INVALID_INPUT", e.getMessage(), null, false)));
        }

        CreateProjectUseCase.Result result = createProjectUseCase.execute(
                new CreateProjectUseCase.Command(userId, title, resolvedText));
        return ResponseEntity.ok(ApiResponse.success(ProjectResponse.from(result)));
    }

    /** Exactly one of pasted text or a .txt upload — spec §4.4. */
    private static String resolveBookText(String bookText, MultipartFile file) {
        boolean hasPastedText = bookText != null && !bookText.isBlank();
        boolean hasFile = file != null && !file.isEmpty();

        if (hasPastedText == hasFile) {
            throw new InputResolutionException("provide exactly one of pasted text or a .txt file upload");
        }
        if (hasPastedText) {
            return bookText;
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".txt")) {
            throw new InputResolutionException("uploaded file must be a .txt file");
        }
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read uploaded file", e);
        }
    }

    private static class InputResolutionException extends RuntimeException {
        InputResolutionException(String message) {
            super(message);
        }
    }
}
