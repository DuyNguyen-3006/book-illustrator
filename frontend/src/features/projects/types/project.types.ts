import type { PipelineStep, ProjectStatus, StepState } from "@/shared/types/pipeline.types";

/** Mirrors ProjectResponse, the reply to a create. */
export interface CreatedProject {
  projectId: number;
  title: string;
}

export interface CreateProjectInput {
  title: string;
  /** Empty when a file is used instead; the two are mutually exclusive. */
  bookText: string;
  file: File | null;
}

/** Mirrors ProjectSummaryResponse. The list deliberately carries no error text. */
export interface ProjectSummary {
  projectId: number;
  title: string;
  createdAt: string;
  status: ProjectStatus;
  currentStep: PipelineStep;
  stepState: StepState;
}
