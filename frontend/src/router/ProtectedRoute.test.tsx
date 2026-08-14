import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import { Route, Routes } from "react-router-dom";
import { ApiError } from "@/shared/lib/errors";
import { renderWithProviders } from "@/test/renderWithProviders";
import * as authService from "@/features/auth/services/auth.service";
import { ProtectedRoute } from "./ProtectedRoute";

vi.mock("@/features/auth/services/auth.service");

const fetchCurrentUser = vi.mocked(authService.fetchCurrentUser);

function renderGuarded() {
  return renderWithProviders(
    <Routes>
      <Route path="/signin" element={<p>Sign in screen</p>} />
      <Route
        path="/projects"
        element={
          <ProtectedRoute>
            <p>Protected content</p>
          </ProtectedRoute>
        }
      />
    </Routes>,
    "/projects",
  );
}

describe("ProtectedRoute", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("sends a signed-out visitor to the sign-in screen, not to the landing page", async () => {
    fetchCurrentUser.mockRejectedValueOnce(
      new ApiError({ code: "UNAUTHENTICATED", message: "Log in first.", retriable: false }, 401),
    );

    renderGuarded();

    expect(await screen.findByText("Sign in screen")).toBeInTheDocument();
  });

  it("renders the screen once the backend confirms the session", async () => {
    fetchCurrentUser.mockResolvedValueOnce({ userId: 1, email: "duy@test.local", name: "Duy" });

    renderGuarded();

    expect(await screen.findByText("Protected content")).toBeInTheDocument();
  });
});
