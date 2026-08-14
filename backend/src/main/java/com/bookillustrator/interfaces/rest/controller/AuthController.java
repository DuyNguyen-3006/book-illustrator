package com.bookillustrator.interfaces.rest.controller;

import com.bookillustrator.application.usecase.user.GetCurrentUserUseCase;
import com.bookillustrator.application.usecase.user.IdentifyUserUseCase;
import com.bookillustrator.interfaces.rest.request.LoginRequest;
import com.bookillustrator.interfaces.rest.response.ApiResponse;
import com.bookillustrator.interfaces.rest.response.UserResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Spec §4.1 — email + name only, no password/OAuth. Session is a plain servlet
 * HttpSession (Spring Boot's default JSESSIONID cookie) — no Spring Security, no
 * custom token scheme needed for this auth model.
 */
@RestController
@RequestMapping("/session")
public class AuthController {

    private static final String SESSION_USER_ID = "userId";
    private static final String SESSION_COOKIE_NAME = "JSESSIONID";

    private final IdentifyUserUseCase identifyUserUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;

    public AuthController(IdentifyUserUseCase identifyUserUseCase, GetCurrentUserUseCase getCurrentUserUseCase) {
        this.identifyUserUseCase = identifyUserUseCase;
        this.getCurrentUserUseCase = getCurrentUserUseCase;
    }

    /**
     * Who am I? The frontend calls this on load to restore the session after a refresh
     * instead of trusting anything it cached client-side.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<UserResponse>> current(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Long userId = session == null ? null : (Long) session.getAttribute(SESSION_USER_ID);
        if (userId == null) {
            return unauthenticated();
        }

        // Empty when the session outlived the user row it names — still a 401, not a 500.
        return getCurrentUserUseCase.execute(userId)
                .map(result -> ResponseEntity.ok(ApiResponse.success(UserResponse.from(result))))
                .orElseGet(AuthController::unauthenticated);
    }

    private static ResponseEntity<ApiResponse<UserResponse>> unauthenticated() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(
                new ApiResponse.ApiError("UNAUTHENTICATED", "Log in first.", null, false)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> login(@RequestBody LoginRequest request,
                                                             HttpServletRequest httpRequest) {
        IdentifyUserUseCase.Result result = identifyUserUseCase.execute(
                new IdentifyUserUseCase.Command(request.email(), request.name()));

        // Regenerate the session ID on successful auth — otherwise a session ID
        // fixed/known before login stays valid after login (session fixation).
        HttpSession session = httpRequest.getSession(true);
        httpRequest.changeSessionId();
        session.setAttribute(SESSION_USER_ID, result.userId());

        return ResponseEntity.ok(ApiResponse.success(UserResponse.from(result)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> logout(HttpServletRequest request,
                                                                     HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        Cookie expired = new Cookie(SESSION_COOKIE_NAME, "");
        expired.setPath("/");
        expired.setMaxAge(0);
        response.addCookie(expired);

        return ResponseEntity.ok(ApiResponse.success(Map.of()));
    }
}
