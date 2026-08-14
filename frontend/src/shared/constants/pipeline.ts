import type { PipelineStep, ProjectStatus, StepState } from "@/shared/types/pipeline.types";

/** What each step is called in the UI. The backend's enum names are not user-facing copy. */
export const STEP_LABELS: Record<PipelineStep, string> = {
  STYLE: "Art style",
  CHARACTERS: "Characters",
  PORTRAITS: "Portraits",
  CHAPTERS: "Chapter prompts",
  ILLUSTRATIONS: "Illustrations",
};

/** Present tense, for the banner shown while a step is actually running. */
export const STEP_RUNNING_LABELS: Record<PipelineStep, string> = {
  STYLE: "Choosing an art style",
  CHARACTERS: "Finding the main characters",
  PORTRAITS: "Painting portraits",
  CHAPTERS: "Writing chapter prompts",
  ILLUSTRATIONS: "Illustrating the chapter",
};

export const PROJECT_STATUS_LABELS: Record<ProjectStatus, string> = {
  DRAFT: "Draft",
  RUNNING: "In progress",
  COMPLETED: "Done",
};

export const STEP_STATE_LABELS: Record<StepState, string> = {
  IDLE: "Ready",
  RUNNING: "Running",
  FAILED: "Failed",
  COMPLETED: "Done",
};
