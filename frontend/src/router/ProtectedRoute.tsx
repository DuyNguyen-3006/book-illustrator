import type { ReactElement } from "react";
import { Navigate } from "react-router-dom";
import { Skeleton } from "@/components/ui/skeleton";
import { useCurrentUser } from "@/features/auth/hooks/useCurrentUser";

/**
 * The session is read from the backend before any protected screen renders, so a
 * refresh and a second tab land on the same picture (frontend-rules §2).
 */
export function ProtectedRoute({ children }: { children: ReactElement }) {
  const { data: user, isPending, isError } = useCurrentUser();

  if (isPending) {
    return (
      <div className="mx-auto flex w-full max-w-5xl flex-col gap-4 px-6 py-12" aria-busy="true">
        <Skeleton className="h-8 w-56" />
        <Skeleton className="h-32 w-full" />
        <Skeleton className="h-32 w-full" />
      </div>
    );
  }

  // Straight to the form, not to the marketing page: someone hitting a project
  // URL signed out wants to sign in, not to read the pitch again.
  if (isError || !user) {
    return <Navigate to="/signin" replace />;
  }

  return children;
}
