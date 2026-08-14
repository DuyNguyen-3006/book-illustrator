import { Check, CircleDashed, Loader2, TriangleAlert } from "lucide-react";
import { STEP_LABELS } from "@/shared/constants/pipeline";
import { stepProgress, type StepProgressState } from "@/shared/lib/projectProgress";
import { cn } from "@/lib/utils";
import type { PipelineStep, ProjectStatus, StepState } from "@/shared/types/pipeline.types";

const ICONS: Record<StepProgressState, typeof Check> = {
  done: Check,
  running: Loader2,
  failed: TriangleAlert,
  pending: CircleDashed,
};

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
 * The five steps as a connected run rather than five equal tiles: finished work
 * stays on screen when a later step fails.
 */
export function PipelineStepper({ status, currentStep, stepState }: PipelineStepperProps) {
  const progress = stepProgress(status, currentStep, stepState);

  return (
    <ol className="grid gap-6 sm:grid-cols-5 sm:gap-0">
      {progress.map(({ step, state }, index) => {
        const Icon = ICONS[state];
        const isLast = index === progress.length - 1;

        return (
          <li
            key={step}
            aria-current={state === "running" || state === "failed" ? "step" : undefined}
            className="relative flex items-start gap-3 sm:flex-col sm:gap-3"
          >
            <div className="flex items-center sm:w-full">
              <span
                className={cn(
                  "flex h-9 w-9 shrink-0 items-center justify-center rounded-full border transition-colors",
                  MARKER_CLASSES[state],
                )}
              >
                <Icon aria-hidden="true" className={cn("h-4 w-4", state === "running" && "animate-spin")} />
              </span>
              {!isLast && (
                <span
                  aria-hidden="true"
                  className={cn(
                    "mx-3 hidden h-px flex-1 sm:block",
                    state === "done" ? "bg-foreground/40" : "bg-border",
                  )}
                />
              )}
            </div>

            <div className="flex flex-col gap-0.5 sm:pr-6">
              <span className="text-sm font-semibold leading-tight">{STEP_LABELS[step]}</span>
              <span
                className={cn(
                  "text-xs",
                  state === "failed" ? "text-destructive" : "text-muted-foreground",
                )}
              >
                {STATE_TEXT[state]}
              </span>
            </div>
          </li>
        );
      })}
    </ol>
  );
}
