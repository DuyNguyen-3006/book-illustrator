import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Route, Routes } from "react-router-dom";
import { ApiError } from "@/shared/lib/errors";
import { renderWithProviders } from "@/test/renderWithProviders";
import * as pipelineService from "../services/pipeline.service";
import { LOCK_TTL_SECONDS } from "../lib/stepRun";
import type { ProjectDetail } from "../types/pipeline.types";
import { ProjectDetailPage } from "./ProjectDetailPage";

vi.mock("../services/pipeline.service");

const fetchProject = vi.mocked(pipelineService.fetchProject);
const runStep = vi.mocked(pipelineService.runStep);

const RUNNING: ProjectDetail = {
  projectId: 7,
  title: "The Wind in the Willows",
  createdAt: "2026-08-12T09:30:00Z",
  bookText: "Once upon a time.",
  status: "RUNNING",
  currentStep: "PORTRAITS",
  stepState: "RUNNING",
  style: null,
  lastError: null,
  stepStartedAt: new Date().toISOString(),
  characters: [],
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

describe("project detail, live states", () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("names the running step and keeps polling until the backend says it finished", async () => {
    fetchProject
      .mockResolvedValueOnce(RUNNING)
      .mockResolvedValue({ ...RUNNING, stepState: "IDLE", currentStep: "CHAPTERS", stepStartedAt: null });

    renderDetail();

    // The banner names the running step; the panel header repeats it, so scope
    // the assertion to the live-status region.
    const banner = await screen.findByRole("status", { name: /running step/i });
    expect(within(banner).getByText(/painting portraits/i)).toBeInTheDocument();
    // Poll picks up the finished state without the user doing anything.
    await waitFor(() => expect(screen.getByRole("button", { name: /run chapter prompts/i })).toBeEnabled(), {
      timeout: 6000,
    });
    expect(fetchProject.mock.calls.length).toBeGreaterThan(1);
  });

  it("does not poll a project that is sitting idle", async () => {
    fetchProject.mockResolvedValue({ ...RUNNING, stepState: "IDLE", stepStartedAt: null });

    renderDetail();

    await screen.findByRole("button", { name: /run portraits/i });
    await new Promise((resolve) => setTimeout(resolve, 3200));
    expect(fetchProject).toHaveBeenCalledTimes(1);
  });

  it("shows the persisted failure next to a retry for that step only", async () => {
    fetchProject.mockResolvedValue({
      ...RUNNING,
      stepState: "FAILED",
      stepStartedAt: null,
      lastError: "The AI service is busy. Try again in a moment.",
    });

    renderDetail();

    expect(await screen.findByRole("alert")).toHaveTextContent("The AI service is busy");
    expect(screen.getByText(/everything generated before this step is kept/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /retry portraits/i })).toBeEnabled();
  });

  it("treats STEP_LOCKED as already running rather than as an error", async () => {
    fetchProject.mockResolvedValue({ ...RUNNING, stepState: "FAILED", stepStartedAt: null, lastError: "boom" });
    runStep.mockRejectedValueOnce(
      new ApiError({ code: "STEP_LOCKED", message: "This step is already running.", retriable: true }, 409),
    );

    renderDetail();

    await userEvent.click(await screen.findByRole("button", { name: /retry portraits/i }));

    expect(await screen.findByText(/already running somewhere else/i)).toBeInTheDocument();
    // The lock message must not be dressed up as a failure banner.
    expect(screen.queryByText("This step is already running.")).not.toBeInTheDocument();
  });

  it("offers a way out once a run outlives the backend's lock TTL", async () => {
    const stale = new Date(Date.now() - (LOCK_TTL_SECONDS + 30) * 1000).toISOString();
    fetchProject.mockResolvedValue({ ...RUNNING, stepStartedAt: stale });

    renderDetail();

    expect(await screen.findByText(/usually means the attempt died/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /retry portraits/i })).toBeEnabled();
  });
});
