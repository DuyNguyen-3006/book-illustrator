import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ApiError } from "@/shared/lib/errors";
import { renderWithProviders } from "@/test/renderWithProviders";
import * as projectService from "../services/project.service";
import type { ProjectSummary } from "../types/project.types";
import { ProjectListPage } from "./ProjectListPage";

vi.mock("../services/project.service");

const fetchProjects = vi.mocked(projectService.fetchProjects);

const project: ProjectSummary = {
  projectId: 7,
  title: "The Wind in the Willows",
  createdAt: "2026-08-12T09:30:00Z",
  status: "RUNNING",
  currentStep: "PORTRAITS",
  stepState: "RUNNING",
};

describe("ProjectListPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("shows a layout-stable skeleton while loading, not a blank screen", () => {
    fetchProjects.mockReturnValueOnce(new Promise(() => {}));

    renderWithProviders(<ProjectListPage />);

    expect(screen.getByRole("status", { name: /loading projects/i })).toBeInTheDocument();
    expect(screen.queryByRole("list")).not.toBeInTheDocument();
  });

  it("distinguishes an empty result from a failure and offers the primary action", async () => {
    fetchProjects.mockResolvedValueOnce([]);

    renderWithProviders(<ProjectListPage />);

    expect(await screen.findByText(/no projects yet/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /new project/i })).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("renders the backend's message on failure and retries the same request", async () => {
    fetchProjects
      .mockRejectedValueOnce(
        new ApiError({ code: "NETWORK_ERROR", message: "Could not reach the server.", retriable: true }, 0),
      )
      .mockResolvedValueOnce([project]);

    renderWithProviders(<ProjectListPage />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Could not reach the server.");
    await userEvent.click(screen.getByRole("button", { name: /try again/i }));

    expect(await screen.findByText(project.title)).toBeInTheDocument();
    await waitFor(() => expect(fetchProjects).toHaveBeenCalledTimes(2));
  });

  it("lists a project with its status and how far the pipeline has got", async () => {
    fetchProjects.mockResolvedValueOnce([project]);

    renderWithProviders(<ProjectListPage />);

    expect(await screen.findByText(project.title)).toBeInTheDocument();
    expect(screen.getByText(/in progress/i)).toBeInTheDocument();
    // Two steps finished before the one currently running.
    expect(screen.getByText(/2 of 5 steps done/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /the wind in the willows/i })).toHaveAttribute(
      "href",
      "/projects/7",
    );
  });
});
