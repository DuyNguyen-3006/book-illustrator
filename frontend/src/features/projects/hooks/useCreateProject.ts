import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { ApiError } from "@/shared/lib/errors";
import { createProject } from "../services/project.service";
import type { CreatedProject, CreateProjectInput } from "../types/project.types";
import { PROJECTS_QUERY_KEY } from "./useProjects";

export function useCreateProject() {
  const queryClient = useQueryClient();

  return useMutation<CreatedProject, ApiError, CreateProjectInput>({
    mutationFn: createProject,
    // The list is stale the moment a project exists; refetch it rather than
    // guessing what the backend stored.
    onSuccess: () => queryClient.invalidateQueries({ queryKey: PROJECTS_QUERY_KEY }),
  });
}
