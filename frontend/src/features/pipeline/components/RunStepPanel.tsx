import { useState } from "react";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";
import { STEP_LABELS, STEP_RUNNING_LABELS } from "@/shared/constants/pipeline";
import { isApiError, messageOf } from "@/shared/lib/errors";
import { useRunStep } from "../hooks/useRunStep";
import { isStepStuck, LOCK_TTL_SECONDS } from "../lib/stepRun";
import type { ProjectDetail } from "../types/pipeline.types";

/**
 * One action for the step the project is actually on. The backend picks which
 * step runs, so this cannot ask for one out of order.
 */
export function RunStepPanel({ project }: { project: ProjectDetail }) {
  const [style, setStyle] = useState("");
  const { mutate: run, isPending, error } = useRunStep(project.projectId);

  const running = project.stepState === "RUNNING";
  const failed = project.stepState === "FAILED";
  const stepLabel = STEP_LABELS[project.currentStep];
  const stuck = running && isStepStuck(project.stepStartedAt);

  // STEP_LOCKED means another tab or request already holds this step. That is the
  // duplicate-call guard doing its job, not a failure to report (frontend-rules §2).
  const lockedElsewhere = isApiError(error) && error.code === "STEP_LOCKED";

  if (project.status === "COMPLETED") {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="text-base">All five steps are done</CardTitle>
          <CardDescription>The portraits and the chapter illustration are below.</CardDescription>
        </CardHeader>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">
          {running ? STEP_RUNNING_LABELS[project.currentStep] : `Next: ${stepLabel}`}
        </CardTitle>
        <CardDescription>
          {running
            ? "This step is already running. Reloading or opening another tab will not start it again."
            : "Each step is one call to Gemini and runs only when you ask for it."}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {project.currentStep === "STYLE" && !running && (
          <div className="flex flex-col gap-2">
            <Label htmlFor="style">Art style (optional)</Label>
            <input
              id="style"
              className={cn(
                "h-11 w-full rounded-md border border-input bg-background px-3 text-sm",
                "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background",
              )}
              placeholder="Leave empty and Gemini will pick one from the book"
              value={style}
              onChange={(event) => setStyle(event.target.value)}
            />
          </div>
        )}

        {lockedElsewhere && (
          <Alert role="status" variant="info" size="sm">
            <AlertDescription>
              This step is already running somewhere else. This page will update when it finishes.
            </AlertDescription>
          </Alert>
        )}

        {error && !lockedElsewhere && (
          <Alert role="alert" variant="destructive" size="sm">
            <AlertDescription>{messageOf(error)}</AlertDescription>
          </Alert>
        )}

        {stuck && (
          <Alert role="status" variant="warning" size="sm">
            <AlertDescription>
              This run has held the step for over {LOCK_TTL_SECONDS} seconds, which usually means the
              attempt died. Retrying now takes the step over rather than starting a second call.
            </AlertDescription>
          </Alert>
        )}

        <div>
          {/* Disabled while the backend reports RUNNING, so a double click or a
              second tab cannot fire the same paid call twice. The exception is a
              run that outlived the lock TTL, which the next attempt reclaims. */}
          <Button
            size="lg"
            disabled={(running && !stuck) || isPending}
            onClick={() => run({ style })}
          >
            {isPending
              ? "Starting"
              : stuck
                ? `Retry ${stepLabel}`
                : running
                  ? "Running"
                  : failed
                    ? `Retry ${stepLabel}`
                    : `Run ${stepLabel}`}
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
