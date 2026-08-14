package com.bookillustrator.interfaces.rest;

import com.bookillustrator.application.usecase.project.CreateProjectUseCase;
import com.bookillustrator.application.usecase.user.IdentifyUserUseCase;
import com.bookillustrator.domain.exception.ProjectNotFoundException;
import com.bookillustrator.interfaces.rest.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;
import java.util.UUID;

/**
 * Central place unmapped/unexpected exceptions become the ApiResponse envelope instead
 * of a raw Spring/Tomcat error page — see .claude/skills/backend-rules/SKILL.md §2 and
 * docs/architecture.md §17. Every endpoint benefits, not just the one that happened to
 * add a try/catch.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IdentifyUserUseCase.InvalidLoginException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidLogin(IdentifyUserUseCase.InvalidLoginException e) {
        return badRequest(e.getMessage());
    }

    @ExceptionHandler(CreateProjectUseCase.InvalidProjectException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidProject(CreateProjectUseCase.InvalidProjectException e) {
        return badRequest(e.getMessage());
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleProjectNotFound(ProjectNotFoundException e) {
        return ResponseEntity.status(404).body(ApiResponse.error(
                new ApiResponse.ApiError("NOT_FOUND", "Project not found.", null, false)));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadPathVariable(MethodArgumentTypeMismatchException e) {
        return badRequest("'" + e.getName() + "' is not a valid value");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return badRequest("uploaded file is too large");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        String traceId = UUID.randomUUID().toString();
        log.error("unhandled exception, trace_id={}", traceId, e);
        return ResponseEntity.internalServerError().body(ApiResponse.error(
                new ApiResponse.ApiError("INTERNAL_ERROR", "Something went wrong.",
                        Map.of("trace_id", traceId), false)));
    }

    private static ResponseEntity<ApiResponse<Void>> badRequest(String message) {
        return ResponseEntity.badRequest().body(ApiResponse.error(
                new ApiResponse.ApiError("INVALID_INPUT", message, null, false)));
    }
}
