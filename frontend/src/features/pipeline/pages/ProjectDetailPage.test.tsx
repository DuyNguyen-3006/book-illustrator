import { beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Route, Routes } from "react-router-dom";
import { ApiError } from "@/shared/lib/errors";
import { renderWithProviders } from "@/test/renderWithProviders";
import * as pipelineService from "../services/pipeline.service";
import type { ProjectDetail } from "../types/pipeline.types";
import { ProjectDetailPage } from "./ProjectDetailPage";

vi.mock("../services/pipeline.service");

const fetchProject = vi.mocked(pipelineService.fetchProject);
const runStep = vi.mocked(pipelineService.runStep);

const baseProject: ProjectDetail = {
  projectId: 7,
  title: "The Wind in the Willows",
  createdAt: "2026-08-12T09:30:00Z",
  bookText: "Once upon a time by the river.",
  status: "RUNNING",
  currentStep: "PORTRAITS",
  stepState: "IDLE",
  style: "Soft watercolour, muted riverbank palette",
  lastError: null,
  stepStartedAt: null,
  characters: [
    { id: 1, name: "Mole", prompt: "A shy mole in a waistcoat", portraitUrl: "/projects/7/characters/1/portrait" },
    { id: 2, name: "Ratty", prompt: "A river rat with a boat", portraitUrl: null },
  ],
  chapters: [],
};

function renderDetail() {
  return renderWithProviders(
    <Routes>
      <Route path="/projects/:projectId" element={<ProjectDetailPage />} />
    </Routes>,
    "/projects/7",
  );
}

describe("ProjectDetailPage", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it("shows a skeleton while the project loads", () => {
    fetchProject.mockReturnValueOnce(new Promise(() => {}));

    renderDetail();

    expect(screen.getByRole("status", { name: /loading project/i })).toBeInTheDocument();
  });

  it("shows the backend's message with a retry when the project fails to load", async () => {
    fetchProject
      .mockRejectedValueOnce(new ApiError({ code: "NOT_FOUND", message: "Project not found.", retriable: false }, 404))
      .mockResolvedValueOnce(baseProject);

    renderDetail();

    expect(await screen.findByRole("alert")).toHaveTextContent("Project not found.");
    // Not retriable, so no retry button is offered.
    expect(screen.queryByRole("button", { name: /try again/i })).not.toBeInTheDocument();
  });

  it("renders the style, the characters and the book text", async () => {
    fetchProject.mockResolvedValue(baseProject);

    renderDetail();

    expect(await screen.findByText(baseProject.title)).toBeInTheDocument();
    expect(screen.getByText(/soft watercolour/i)).toBeInTheDocument();
    expect(screen.getByText("Mole")).toBeInTheDocument();
    expect(screen.getByAltText(/portrait of mole/i)).toHaveAttribute(
      "src",
      "/api/projects/7/characters/1/portrait",
    );
    // A character without a portrait says so instead of rendering a broken image.
    expect(screen.getByText(/portrait not generated yet/i)).toBeInTheDocument();

    await userEvent.click(screen.getByRole("button", { name: /read/i }));
    expect(screen.getByText(/once upon a time by the river/i)).toBeInTheDocument();
  });

  it("names the step the button will run and runs it", async () => {
    fetchProject.mockResolvedValue(baseProject);
    runStep.mockResolvedValueOnce({
      projectId: 7,
      status: "RUNNING",
      currentStep: "PORTRAITS",
      stepState: "RUNNING",
      style: baseProject.style,
    });

    renderDetail();

    const button = await screen.findByRole("button", { name: /run portraits/i });
    await userEvent.click(button);

    await waitFor(() => expect(runStep).toHaveBeenCalledTimes(1));
  });

  it("offers no run button while the backend reports the step already running", async () => {
    fetchProject.mockResolvedValue({ ...baseProject, stepState: "RUNNING", stepStartedAt: "2026-08-12T09:31:00Z" });

    renderDetail();

    expect(await screen.findByRole("button", { name: /running/i })).toBeDisabled();
    expect(runStep).not.toHaveBeenCalled();
  });

  it("labels the action as a retry after a failure", async () => {
    fetchProject.mockResolvedValue({
      ...baseProject,
      stepState: "FAILED",
      lastError: "The AI service is busy. Try again in a moment.",
    });

    renderDetail();

    expect(await screen.findByRole("button", { name: /retry portraits/i })).toBeEnabled();
  });
});
