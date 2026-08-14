import { jsonBody, request } from "@/shared/lib/http";
import type { ProjectDetail, RunStepResult } from "../types/pipeline.types";

export async function fetchProject(projectId: number): Promise<ProjectDetail> {
  const { data } = await request<ProjectDetail>(`/projects/${projectId}`);
  return data;
}

/**
 * Runs whichever step the project is on. The backend picks the step, not the
 * client, so a stale tab cannot ask for the wrong one. `style` only applies to
 * step 1 and is ignored otherwise.
 */
export async function runStep(projectId: number, style?: string): Promise<RunStepResult> {
  const { data } = await request<RunStepResult>(
    `/projects/${projectId}/run-step`,
    jsonBody("POST", { style: style?.trim() || null }),
  );
  return data;
}
