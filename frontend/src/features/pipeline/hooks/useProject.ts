import { useQuery } from "@tanstack/react-query";
import type { ApiError } from "@/shared/lib/errors";
import { fetchProject } from "../services/pipeline.service";
import type { ProjectDetail } from "../types/pipeline.types";

export const projectQueryKey = (projectId: number) => ["project", projectId] as const;

/**
 * On mount this is what decides what the screen shows: pipeline state comes from
 * the backend, never from anything the client remembers (frontend-rules §2).
 * Polling while a step runs arrives with #24.
 */
export function useProject(projectId: number) {
  return useQuery<ProjectDetail, ApiError>({
    queryKey: projectQueryKey(projectId),
    queryFn: () => fetchProject(projectId),
  });
}
