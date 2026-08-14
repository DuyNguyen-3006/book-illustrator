import type { PipelineStep, ProjectStatus, StepState } from "@/shared/types/pipeline.types";

/** Mirrors ProjectSummaryResponse. The list deliberately carries no error text. */
export interface ProjectSummary {
  projectId: number;
  title: string;
  createdAt: string;
  status: ProjectStatus;
  currentStep: PipelineStep;
  stepState: StepState;
}
