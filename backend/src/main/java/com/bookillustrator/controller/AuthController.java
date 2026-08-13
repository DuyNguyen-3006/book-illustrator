package com.bookillustrator.controller;

import com.bookillustrator.application.LoginUseCase;
import com.bookillustrator.controller.dto.ApiResponse;
import com.bookillustrator.controller.dto.LoginRequest;
import com.bookillustrator.controller.dto.UserResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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

    private final LoginUseCase loginUseCase;

    public AuthController(LoginUseCase loginUseCase) {
        this.loginUseCase = loginUseCase;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> login(@RequestBody LoginRequest request,
                                                             HttpServletRequest httpRequest) {
        try {
            LoginUseCase.Result result = loginUseCase.execute(
                    new LoginUseCase.Command(request.email(), request.name()));

            // Regenerate the session ID on successful auth — otherwise a session ID
            // fixed/known before login stays valid after login (session fixation).
            HttpSession session = httpRequest.getSession(true);
            httpRequest.changeSessionId();
            session.setAttribute(SESSION_USER_ID, result.userId());

            return ResponseEntity.ok(ApiResponse.success(UserResponse.from(result)));
        } catch (LoginUseCase.InvalidLoginException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    new ApiResponse.ApiError("INVALID_INPUT", e.getMessage(), null, false)));
        }
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
