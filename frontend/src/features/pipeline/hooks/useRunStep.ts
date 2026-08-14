import { useMutation, useQueryClient } from "@tanstack/react-query";
import { PROJECTS_QUERY_KEY } from "@/features/projects/hooks/useProjects";
import type { ApiError } from "@/shared/lib/errors";
import { runStep } from "../services/pipeline.service";
import type { RunStepResult } from "../types/pipeline.types";
import { projectQueryKey } from "./useProject";

export function useRunStep(projectId: number) {
  const queryClient = useQueryClient();

  return useMutation<RunStepResult, ApiError, { style?: string } | void>({
    mutationFn: (variables) => runStep(projectId, variables?.style),
    // Whatever the call did, the truth is now on the server: refetch rather than
    // patching the cache with an assumed outcome (frontend-rules §2).
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: projectQueryKey(projectId) });
      void queryClient.invalidateQueries({ queryKey: PROJECTS_QUERY_KEY });
    },
  });
}
