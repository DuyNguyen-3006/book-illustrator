import { useNavigate } from "react-router-dom";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { messageOf } from "@/shared/lib/errors";
import { useLogout } from "../hooks/useLogout";

export function SignOutButton() {
  const navigate = useNavigate();
  const { mutate: signOut, isPending, error } = useLogout();

  return (
    <div className="flex items-center gap-3">
      {error && (
        <Alert role="alert" variant="destructive" size="sm" className="py-1">
          <AlertDescription className="text-xs">{messageOf(error)}</AlertDescription>
        </Alert>
      )}
      <Button
        variant="ghost"
        size="sm"
        disabled={isPending}
        // The redirect waits for the server to confirm: the cookie is only gone
        // once DELETE /session succeeds, and useLogout clears the cache with it.
        onClick={() => signOut(undefined, { onSuccess: () => navigate("/", { replace: true }) })}
      >
        {isPending ? "Signing out" : "Sign out"}
      </Button>
    </div>
  );
}
