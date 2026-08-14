import { Check, TriangleAlert } from "lucide-react";
import { STEP_LABELS } from "@/shared/constants/pipeline";
import { stepProgress, type StepProgressState } from "@/shared/lib/projectProgress";
import { cn } from "@/lib/utils";
import type { PipelineStep, ProjectStatus, StepState } from "@/shared/types/pipeline.types";

const STATE_TEXT: Record<StepProgressState, string> = {
  done: "Done",
  running: "Running",
  failed: "Failed",
  pending: "Not started",
};

const MARKER_CLASSES: Record<StepProgressState, string> = {
  done: "border-foreground bg-foreground text-background",
  running: "border-accent bg-accent text-accent-foreground",
  failed: "border-destructive bg-destructive text-destructive-foreground",
  pending: "border-border bg-card text-muted-foreground",
};

interface PipelineStepperProps {
  status: ProjectStatus;
  currentStep: PipelineStep;
  stepState: StepState;
}

/**
 * Five markers on one centred line. The connector between two steps fills as the
 * left one finishes, so progress reads as travel along the pipeline rather than
 * five separate tiles; the running step spins in place.
 */
export function PipelineStepper({ status, currentStep, stepState }: PipelineStepperProps) {
  const progress = stepProgress(status, currentStep, stepState);

  return (
    <ol className="grid grid-cols-5 gap-2">
      {progress.map(({ step, state }, index) => {
        const isLast = index === progress.length - 1;

        return (
          <li
            key={step}
            aria-current={state === "running" || state === "failed" ? "step" : undefined}
            className="relative flex flex-col items-center gap-3 text-center"
          >
            {!isLast && (
              // Sits on the marker's centre line, spanning to the next marker.
              <span
                aria-hidden="true"
                className="absolute left-[calc(50%+1.5rem)] right-[calc(-50%+1.5rem)] top-[1.125rem] h-0.5 overflow-hidden rounded-full bg-border"
              >
                <span
                  className={cn(
                    "block h-full bg-foreground transition-[width] duration-700 ease-out",
                    state === "done" ? "w-full" : "w-0",
                  )}
                />
              </span>
            )}

            <span
              className={cn(
                "relative z-10 flex h-9 w-9 items-center justify-center rounded-full border transition-colors",
                MARKER_CLASSES[state],
              )}
            >
              {state === "running" ? (
                <span
                  aria-hidden="true"
                  className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent"
                />
              ) : state === "failed" ? (
                <TriangleAlert aria-hidden="true" className="h-4 w-4" />
              ) : state === "done" ? (
                <Check aria-hidden="true" className="h-4 w-4" />
              ) : (
                <span aria-hidden="true" className="h-2 w-2 rounded-full bg-current" />
              )}

              {state === "running" && (
                <span
                  aria-hidden="true"
                  className="absolute inset-0 animate-ping rounded-full border border-accent opacity-60"
                />
              )}
            </span>

            <span className="flex flex-col gap-0.5">
              <span className="text-sm font-semibold leading-tight">{STEP_LABELS[step]}</span>
              <span
                className={cn(
                  "text-xs",
                  state === "failed" ? "text-destructive" : "text-muted-foreground",
                )}
              >
                {STATE_TEXT[state]}
              </span>
            </span>
          </li>
        );
      })}
    </ol>
  );
}
