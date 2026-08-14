import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ApiError } from "@/shared/lib/errors";
import { renderWithProviders } from "@/test/renderWithProviders";
import * as authService from "../services/auth.service";
import { SignOutButton } from "./SignOutButton";

vi.mock("../services/auth.service");

const logout = vi.mocked(authService.logout);

describe("SignOutButton", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("ends the session on the server", async () => {
    logout.mockResolvedValueOnce(undefined);

    renderWithProviders(<SignOutButton />);
    await userEvent.click(screen.getByRole("button", { name: /sign out/i }));

    expect(logout).toHaveBeenCalledTimes(1);
  });

  it("says so when signing out fails instead of pretending it worked", async () => {
    logout.mockRejectedValueOnce(
      new ApiError({ code: "NETWORK_ERROR", message: "Could not reach the server.", retriable: true }, 0),
    );

    renderWithProviders(<SignOutButton />);
    await userEvent.click(screen.getByRole("button", { name: /sign out/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Could not reach the server.");
    expect(screen.getByRole("button", { name: /sign out/i })).toBeEnabled();
  });
});
