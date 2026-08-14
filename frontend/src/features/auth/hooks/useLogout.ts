import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { ApiError } from "@/shared/lib/errors";
import { logout } from "../services/auth.service";

export function useLogout() {
  const queryClient = useQueryClient();

  return useMutation<void, ApiError, void>({
    mutationFn: logout,
    // Everything cached belonged to the user who just signed out.
    onSuccess: () => queryClient.clear(),
  });
}
