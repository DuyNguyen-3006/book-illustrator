import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ApiError } from "@/shared/lib/errors";
import { renderWithProviders } from "@/test/renderWithProviders";
import * as authService from "../services/auth.service";
import { IdentityForm } from "./IdentityForm";

vi.mock("../services/auth.service");

const login = vi.mocked(authService.login);

describe("IdentityForm", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("rejects a malformed email without calling the API", async () => {
    renderWithProviders(<IdentityForm onSignedIn={vi.fn()} />);

    await userEvent.type(screen.getByLabelText(/name/i), "Duy");
    await userEvent.type(screen.getByLabelText(/email/i), "not-an-email");
    await userEvent.click(screen.getByRole("button", { name: /continue/i }));

    expect(await screen.findByText(/valid email address/i)).toBeInTheDocument();
    expect(login).not.toHaveBeenCalled();
  });

  it("requires a name", async () => {
    renderWithProviders(<IdentityForm onSignedIn={vi.fn()} />);

    await userEvent.type(screen.getByLabelText(/email/i), "duy@test.local");
    await userEvent.click(screen.getByRole("button", { name: /continue/i }));

    expect(await screen.findByText(/enter your name/i)).toBeInTheDocument();
    expect(login).not.toHaveBeenCalled();
  });

  it("shows the backend's own message and stays usable after a failure", async () => {
    login.mockRejectedValueOnce(
      new ApiError({ code: "INVALID_INPUT", message: "email must be a valid address", retriable: false }, 400),
    );

    renderWithProviders(<IdentityForm onSignedIn={vi.fn()} />);
    await userEvent.type(screen.getByLabelText(/name/i), "Duy");
    await userEvent.type(screen.getByLabelText(/email/i), "duy@test.local");
    await userEvent.click(screen.getByRole("button", { name: /continue/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("email must be a valid address");
    expect(screen.getByRole("button", { name: /continue/i })).toBeEnabled();
  });

  it("signs in and reports it once the backend confirms", async () => {
    login.mockResolvedValueOnce({ userId: 1, email: "duy@test.local", name: "Duy" });
    const onSignedIn = vi.fn();

    renderWithProviders(<IdentityForm onSignedIn={onSignedIn} />);
    await userEvent.type(screen.getByLabelText(/name/i), "  Duy  ");
    await userEvent.type(screen.getByLabelText(/email/i), "duy@test.local");
    await userEvent.click(screen.getByRole("button", { name: /continue/i }));

    await waitFor(() => expect(onSignedIn).toHaveBeenCalledOnce());
    // Whitespace is trimmed before it ever reaches the API.
    expect(login.mock.calls[0][0]).toEqual({ email: "duy@test.local", name: "Duy" });
  });
});
