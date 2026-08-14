import { useQuery } from "@tanstack/react-query";
import type { ApiError } from "@/shared/lib/errors";
import { fetchCurrentUser } from "../services/auth.service";
import type { User } from "../types/auth.types";

export const SESSION_QUERY_KEY = ["session"] as const;

/**
 * Who is signed in, according to the backend. Identity is never cached in the
 * browser as a source of truth (frontend-rules §2), so a reload re-asks.
 */
export function useCurrentUser() {
  return useQuery<User, ApiError>({
    queryKey: SESSION_QUERY_KEY,
    queryFn: fetchCurrentUser,
    // A 401 is the answer "nobody is signed in", not a transient failure.
    retry: false,
    staleTime: 30_000,
  });
}
