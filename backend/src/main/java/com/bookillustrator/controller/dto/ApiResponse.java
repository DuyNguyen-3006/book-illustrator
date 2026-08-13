package com.bookillustrator.controller.dto;

/**
 * The single BE-FE response envelope every endpoint returns.
 * See .claude/skills/backend-rules/SKILL.md §1 — status is exactly one of
 * "success" | "error" | "loading"; data and error are mutually exclusive.
 */
public record ApiResponse<T>(String status, T data, ApiError error) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("success", data, null);
    }

    public static <T> ApiResponse<T> error(ApiError error) {
        return new ApiResponse<>("error", null, error);
    }

    public record ApiError(String code, String message, Object details, boolean retriable) {}
}
