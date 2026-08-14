import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { isApiError, messageOf } from "@/shared/lib/errors";

interface ErrorStateProps {
  error: unknown;
  onRetry?: () => void;
  /** Disable while a retry is in flight so one click cannot become two requests. */
  retrying?: boolean;
}

/**
 * Error is its own state, never rendered as "nothing here" (frontend-rules §1).
 * When the backend says the failure is not retriable, no Retry button is offered.
 */
export function ErrorState({ error, onRetry, retrying = false }: ErrorStateProps) {
  const retriable = !isApiError(error) || error.retriable;

  return (
    <Alert role="alert" variant="destructive">
      <AlertTitle>That did not load</AlertTitle>
      <AlertDescription className="flex flex-col items-start gap-3">
        <span>{messageOf(error)}</span>
        {onRetry && retriable && (
          <Button variant="outline" size="sm" onClick={onRetry} disabled={retrying}>
            {retrying ? "Retrying" : "Try again"}
          </Button>
        )}
      </AlertDescription>
    </Alert>
  );
}
