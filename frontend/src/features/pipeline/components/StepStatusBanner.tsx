import { useEffect, useState } from "react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { STEP_LABELS, STEP_RUNNING_LABELS } from "@/shared/constants/pipeline";
import { elapsedSeconds, formatElapsed, SLOW_STEP_SECONDS } from "../lib/stepRun";
import type { ProjectDetail } from "../types/pipeline.types";

/**
 * Says which step is running and for how long, or which one failed and why.
 * A bare spinner for a 30-second call reads as a frozen app (frontend-rules §3).
 */
export function StepStatusBanner({ project }: { project: ProjectDetail }) {
  const running = project.stepState === "RUNNING";
  const elapsed = useElapsed(running ? project.stepStartedAt : null);

  if (project.stepState === "FAILED") {
    return (
      <Alert role="alert" variant="destructive">
        <AlertTitle>{STEP_LABELS[project.currentStep]} failed</AlertTitle>
        <AlertDescription className="flex flex-col gap-1">
          <span>{project.lastError ?? "The step did not finish."}</span>
          <span className="text-xs opacity-90">
            Everything generated before this step is kept. Retrying runs this step only.
          </span>
        </AlertDescription>
      </Alert>
    );
  }

  if (!running) {
    return null;
  }

  return (
    // Named so it is distinguishable from the page's loading region, both to a
    // screen reader and to anything querying by role.
    <Alert role="status" aria-label="Running step" aria-live="polite" variant="info">
      <AlertTitle>
        {STEP_RUNNING_LABELS[project.currentStep]}
        {elapsed === null ? "" : ` · ${formatElapsed(elapsed)}`}
      </AlertTitle>
      <AlertDescription>
        {elapsed !== null && elapsed >= SLOW_STEP_SECONDS
          ? "Image steps can take a while. Your progress is saved, and this page keeps itself up to date."
          : "Started. This page updates itself while the step runs."}
      </AlertDescription>
    </Alert>
  );
}

/** Ticks once a second, only while something is actually running. */
function useElapsed(startedAt: string | null): number | null {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (!startedAt) {
      return;
    }
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [startedAt]);

  return elapsedSeconds(startedAt, now);
}
