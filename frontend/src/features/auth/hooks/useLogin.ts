import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { ApiError } from "@/shared/lib/errors";
import { login } from "../services/auth.service";
import type { LoginPayload, User } from "../types/auth.types";
import { SESSION_QUERY_KEY } from "./useCurrentUser";

export function useLogin() {
  const queryClient = useQueryClient();

  return useMutation<User, ApiError, LoginPayload>({
    mutationFn: login,
    onSuccess: (user) => queryClient.setQueryData(SESSION_QUERY_KEY, user),
  });
}
