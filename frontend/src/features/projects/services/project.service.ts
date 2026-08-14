import { request } from "@/shared/lib/http";
import type { ProjectSummary } from "../types/project.types";

export async function fetchProjects(): Promise<ProjectSummary[]> {
  const { data } = await request<ProjectSummary[]>("/projects");
  return data;
}
