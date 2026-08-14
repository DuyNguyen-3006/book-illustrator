package com.bookillustrator.interfaces.rest.controller;

import com.bookillustrator.application.port.output.ImageStorage;
import com.bookillustrator.application.usecase.pipeline.PipelineStepResult;
import com.bookillustrator.application.usecase.pipeline.RunPipelineStepUseCase;
import com.bookillustrator.application.usecase.project.CreateProjectUseCase;
import com.bookillustrator.application.usecase.project.GetGeneratedImageUseCase;
import com.bookillustrator.application.usecase.project.GetProjectUseCase;
import com.bookillustrator.application.usecase.project.GetProjectsUseCase;
import com.bookillustrator.interfaces.rest.request.RunStepRequest;
import com.bookillustrator.interfaces.rest.response.ApiResponse;
import com.bookillustrator.interfaces.rest.response.ProjectDetailResponse;
import com.bookillustrator.interfaces.rest.response.ProjectResponse;
import com.bookillustrator.interfaces.rest.response.ProjectSummaryResponse;
import com.bookillustrator.interfaces.rest.response.RunStepResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/projects")
public class ProjectController {

    private static final String SESSION_USER_ID = "userId";

    private final CreateProjectUseCase createProjectUseCase;
    private final GetProjectsUseCase getProjectsUseCase;
    private final GetProjectUseCase getProjectUseCase;
    private final GetGeneratedImageUseCase getGeneratedImageUseCase;
    private final RunPipelineStepUseCase runPipelineStepUseCase;

    public ProjectController(CreateProjectUseCase createProjectUseCase, GetProjectsUseCase getProjectsUseCase,
                              GetProjectUseCase getProjectUseCase, GetGeneratedImageUseCase getGeneratedImageUseCase,
                              RunPipelineStepUseCase runPipelineStepUseCase) {
        this.createProjectUseCase = createProjectUseCase;
        this.getProjectsUseCase = getProjectsUseCase;
        this.getProjectUseCase = getProjectUseCase;
        this.getGeneratedImageUseCase = getGeneratedImageUseCase;
        this.runPipelineStepUseCase = runPipelineStepUseCase;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> create(
            @RequestParam("title") String title,
            @RequestParam(value = "bookText", required = false) String bookText,
            @RequestParam(value = "file", required = false) MultipartFile file,
            HttpSession session) {

        Long userId = authenticatedUserId(session);
        if (userId == null) {
            return unauthenticated();
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

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectSummaryResponse>>> list(HttpSession session) {
        Long userId = authenticatedUserId(session);
        if (userId == null) {
            return unauthenticated();
        }

        List<ProjectSummaryResponse> projects = getProjectsUseCase.execute(userId).stream()
                .map(ProjectSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(projects));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectDetailResponse>> detail(
            @PathVariable("id") long id, HttpSession session) {
        Long userId = authenticatedUserId(session);
        if (userId == null) {
            return unauthenticated();
        }

        GetProjectUseCase.Result result = getProjectUseCase.execute(id, userId);
        return ResponseEntity.ok(ApiResponse.success(ProjectDetailResponse.from(result)));
    }

    @GetMapping("/{id}/characters/{characterId}/portrait")
    public ResponseEntity<?> portrait(@PathVariable("id") long id,
                                       @PathVariable("characterId") long characterId,
                                       HttpSession session) {
        Long userId = authenticatedUserId(session);
        if (userId == null) {
            return unauthenticated();
        }
        return imageOrNotFound(getGeneratedImageUseCase.portrait(id, characterId, userId));
    }

    @GetMapping("/{id}/chapters/{chapterId}/illustration")
    public ResponseEntity<?> illustration(@PathVariable("id") long id,
                                           @PathVariable("chapterId") long chapterId,
                                           HttpSession session) {
        Long userId = authenticatedUserId(session);
        if (userId == null) {
            return unauthenticated();
        }
        return imageOrNotFound(getGeneratedImageUseCase.illustration(id, chapterId, userId));
    }

    /**
     * Image bytes on success; on a miss the usual envelope, because "not generated yet"
     * is something the frontend reads, not a broken image.
     */
    private static ResponseEntity<?> imageOrNotFound(Optional<ImageStorage.StoredImage> image) {
        return image
                .<ResponseEntity<?>>map(stored -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(stored.mimeType()))
                        .body(stored.bytes()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(
                        new ApiResponse.ApiError("NOT_FOUND", "That image has not been generated yet.", null, false))));
    }

    private static <T> ResponseEntity<ApiResponse<T>> unauthenticated() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(
                new ApiResponse.ApiError("UNAUTHENTICATED", "Log in first.", null, false)));
    }

    @PostMapping("/{id}/run-step")
    public ResponseEntity<ApiResponse<RunStepResponse>> runStep(
            @PathVariable("id") long id,
            @RequestBody(required = false) RunStepRequest request,
            HttpSession session) {
        Long userId = authenticatedUserId(session);
        if (userId == null) {
            return unauthenticated();
        }

        String style = request == null ? null : request.style();
        PipelineStepResult result = runPipelineStepUseCase.execute(id, userId, style);

        RunStepResponse body = RunStepResponse.from(result.project());
        // Spec §4.3: the UI must show which step is running, not a bare spinner — the
        // envelope's own "loading" status (backend-rules §1) carries that, distinct
        // from "success".
        return ResponseEntity.ok(result.inProgress() ? ApiResponse.loading(body) : ApiResponse.success(body));
    }

    private static Long authenticatedUserId(HttpSession session) {
        return (Long) session.getAttribute(SESSION_USER_ID);
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
