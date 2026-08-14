import type { PipelineStep, ProjectStatus, StepState } from "@/shared/types/pipeline.types";

export interface CharacterItem {
  id: number;
  name: string;
  prompt: string;
  /** API path, null until the portrait has been generated. */
  portraitUrl: string | null;
}

export interface ChapterItem {
  id: number;
  name: string;
  prompt: string;
  illustrationUrl: string | null;
}

/** Mirrors ProjectDetailResponse. */
export interface ProjectDetail {
  projectId: number;
  title: string;
  createdAt: string;
  bookText: string;
  status: ProjectStatus;
  currentStep: PipelineStep;
  stepState: StepState;
  style: string | null;
  /** The already-classified, user-safe failure message, null when nothing failed. */
  lastError: string | null;
  stepStartedAt: string | null;
  characters: CharacterItem[];
  chapters: ChapterItem[];
}

/** Mirrors RunStepResponse. */
export interface RunStepResult {
  projectId: number;
  status: ProjectStatus;
  currentStep: PipelineStep;
  stepState: StepState;
  style: string | null;
}
