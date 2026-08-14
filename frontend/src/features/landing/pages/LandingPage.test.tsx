import { describe, expect, it } from "vitest";
import { screen } from "@testing-library/react";
import { renderWithProviders } from "@/test/renderWithProviders";
import { LandingPage } from "./LandingPage";

describe("LandingPage", () => {
  it("sends every call to action to the sign-in screen", () => {
    renderWithProviders(<LandingPage />);

    const signInLinks = screen
      .getAllByRole("link")
      .filter((link) => link.getAttribute("href") === "/signin");

    expect(signInLinks.length).toBeGreaterThanOrEqual(2);
    expect(screen.getByRole("link", { name: /get started/i })).toHaveAttribute("href", "/signin");
  });

  it("names all five pipeline steps, in order", () => {
    renderWithProviders(<LandingPage />);

    const steps = ["Art style", "Characters", "Portraits", "Chapter prompts", "Illustrations"];
    steps.forEach((step) => expect(screen.getByText(step)).toBeInTheDocument());
  });

  it("does not ask for a password anywhere", () => {
    renderWithProviders(<LandingPage />);

    expect(screen.getByText(/no password/i)).toBeInTheDocument();
  });
});
