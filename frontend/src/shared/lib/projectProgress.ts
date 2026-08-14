import { PIPELINE_STEPS, type PipelineStep, type ProjectStatus, type StepState } from "@/shared/types/pipeline.types";

export type StepProgressState = "done" | "running" | "failed" | "pending";

export interface StepProgress {
  step: PipelineStep;
  state: StepProgressState;
}

/**
 * Turns the backend's (status, currentStep, stepState) into one state per step.
 * Progress is read from real step positions, never from an invented percentage
 * (frontend-rules §3).
 */
export function stepProgress(
  status: ProjectStatus,
  currentStep: PipelineStep,
  stepState: StepState,
): StepProgress[] {
  const currentIndex = PIPELINE_STEPS.indexOf(currentStep);

  return PIPELINE_STEPS.map((step, index) => {
    if (status === "COMPLETED") {
      return { step, state: "done" as const };
    }
    if (index < currentIndex) {
      return { step, state: "done" as const };
    }
    if (index > currentIndex) {
      return { step, state: "pending" as const };
    }

    switch (stepState) {
      case "COMPLETED":
        return { step, state: "done" as const };
      case "RUNNING":
        return { step, state: "running" as const };
      case "FAILED":
        return { step, state: "failed" as const };
      default:
        return { step, state: "pending" as const };
    }
  });
}

/** How many of the five steps have actually finished. */
export function completedStepCount(progress: StepProgress[]): number {
  return progress.filter((entry) => entry.state === "done").length;
}
