import { useQuery } from "@tanstack/react-query";
import type { ApiError } from "@/shared/lib/errors";
import { fetchProjects } from "../services/project.service";
import type { ProjectSummary } from "../types/project.types";

export const PROJECTS_QUERY_KEY = ["projects"] as const;

export function useProjects() {
  return useQuery<ProjectSummary[], ApiError>({
    queryKey: PROJECTS_QUERY_KEY,
    queryFn: fetchProjects,
  });
}
