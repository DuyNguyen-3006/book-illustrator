/** Mirrors the backend enums — domain/enums/{PipelineStep,StepState,ProjectStatus}.java. */

export const PIPELINE_STEPS = ["STYLE", "CHARACTERS", "PORTRAITS", "CHAPTERS", "ILLUSTRATIONS"] as const;

export type PipelineStep = (typeof PIPELINE_STEPS)[number];

export type StepState = "IDLE" | "RUNNING" | "FAILED" | "COMPLETED";

export type ProjectStatus = "DRAFT" | "RUNNING" | "COMPLETED";
