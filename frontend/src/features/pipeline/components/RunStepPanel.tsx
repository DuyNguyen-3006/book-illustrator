import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";
import { useToast } from "@/shared/components/ToastProvider";
import { STEP_LABELS, STEP_RUNNING_LABELS } from "@/shared/constants/pipeline";
import { isApiError, messageOf } from "@/shared/lib/errors";
import { useRunStep } from "../hooks/useRunStep";
import { isStepStuck, LOCK_TTL_SECONDS } from "../lib/stepRun";
import type { ProjectDetail } from "../types/pipeline.types";

/**
 * One action for the step the project is actually on. The backend picks which
 * step runs, so this cannot ask for one out of order.
 *
 * Outcomes of pressing the button are transient and go to a toast; the failed
 * step itself stays on the page in StepStatusBanner, because that state has to
 * survive a reload.
 */
export function RunStepPanel({ project }: { project: ProjectDetail }) {
  const [style, setStyle] = useState("");
  const toast = useToast();
  const { mutate: run, isPending } = useRunStep(project.projectId);

  const running = project.stepState === "RUNNING";
  const failed = project.stepState === "FAILED";
  const stepLabel = STEP_LABELS[project.currentStep];
  const stuck = running && isStepStuck(project.stepStartedAt);

  function handleRun() {
    run(
      { style },
      {
        onSuccess: () => toast.info(`${STEP_RUNNING_LABELS[project.currentStep]}. This page follows along.`),
        onError: (error) => {
          // STEP_LOCKED is the duplicate-call guard doing its job, not a failure.
          if (isApiError(error) && error.code === "STEP_LOCKED") {
            toast.info("That step is already running somewhere else. This page will catch up.");
            return;
          }
          toast.error(messageOf(error));
        },
      },
    );
  }

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

        {stuck && (
          <p className="text-sm text-muted-foreground">
            This run has held the step for over {LOCK_TTL_SECONDS} seconds, which usually means the
            attempt died. Retrying takes the step over rather than starting a second call.
          </p>
        )}

        <div>
          {/* Disabled while the backend reports RUNNING, so a double click or a
              second tab cannot fire the same paid call twice. The exception is a
              run that outlived the lock TTL, which the next attempt reclaims. */}
          <Button size="lg" disabled={(running && !stuck) || isPending} onClick={handleRun}>
            {isPending
              ? "Starting"
              : stuck || failed
                ? `Retry ${stepLabel}`
                : running
                  ? "Running"
                  : `Run ${stepLabel}`}
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
