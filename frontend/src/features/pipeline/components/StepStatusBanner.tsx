import { useEffect, useRef, useState } from "react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { useToast } from "@/shared/components/ToastProvider";
import { STEP_LABELS, STEP_RUNNING_LABELS } from "@/shared/constants/pipeline";
import { elapsedSeconds, formatElapsed, SLOW_STEP_SECONDS } from "../lib/stepRun";
import type { ProjectDetail } from "../types/pipeline.types";

/**
 * Says which step is running and for how long. A bare spinner for a 30-second
 * call reads as a frozen app (frontend-rules §3).
 *
 * A failed step is reported through a toast rather than a block parked in the
 * layout, on the user's explicit call. The persisted reason still reaches them
 * after a reload because the toast fires from the stored lastError, and the
 * stepper keeps that step marked Failed.
 */
export function StepStatusBanner({ project }: { project: ProjectDetail }) {
  const toast = useToast();
  const running = project.stepState === "RUNNING";
  const elapsed = useElapsed(running ? project.stepStartedAt : null);
  const announced = useRef<string | null>(null);

  useEffect(() => {
    if (project.stepState !== "FAILED") {
      announced.current = null;
      return;
    }
    const message = project.lastError ?? "The step did not finish.";
    // Announce a given failure once, not on every poll.
    if (announced.current === message) {
      return;
    }
    announced.current = message;
    toast.error(`${STEP_LABELS[project.currentStep]} failed. ${message}`);
  }, [project.stepState, project.lastError, project.currentStep, toast]);

  if (!running) {
    return null;
  }

  return (
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
