import { request } from "@/shared/lib/http";
import type { CreatedProject, CreateProjectInput, ProjectSummary } from "../types/project.types";

export async function fetchProjects(): Promise<ProjectSummary[]> {
  const { data } = await request<ProjectSummary[]>("/projects");
  return data;
}

/**
 * POST /projects takes request params, not a JSON body, so this posts FormData.
 * Exactly one of bookText or file is sent, matching the backend's own rule.
 */
export async function createProject(input: CreateProjectInput): Promise<CreatedProject> {
  const form = new FormData();
  form.append("title", input.title);
  if (input.file) {
    form.append("file", input.file);
  } else {
    form.append("bookText", input.bookText);
  }

  const { data } = await request<CreatedProject>("/projects", { method: "POST", body: form });
  return data;
}
