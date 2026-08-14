import { useQuery } from "@tanstack/react-query";
import type { ApiError } from "@/shared/lib/errors";
import { fetchProject } from "../services/pipeline.service";
import type { ProjectDetail } from "../types/pipeline.types";

export const projectQueryKey = (projectId: number) => ["project", projectId] as const;

/** Slow enough not to hammer a backend that is waiting on Gemini, fast enough to feel live. */
const POLL_INTERVAL_MS = 2500;

/**
 * On mount this is what decides what the screen shows: pipeline state comes from
 * the backend, never from anything the client remembers (frontend-rules §2).
 *
 * Polling runs only while a step is actually running, pauses while the tab is
 * hidden, and stops on its own once the backend reports a finished or failed
 * step - no timer to clear by hand, no poller left running after unmount.
 */
export function useProject(projectId: number) {
  return useQuery<ProjectDetail, ApiError>({
    queryKey: projectQueryKey(projectId),
    queryFn: () => fetchProject(projectId),
    refetchInterval: (query) => (query.state.data?.stepState === "RUNNING" ? POLL_INTERVAL_MS : false),
    refetchIntervalInBackground: false,
  });
}
