import { Check, CircleDashed, Loader2, TriangleAlert } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
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

interface PipelineStepperProps {
  status: ProjectStatus;
  currentStep: PipelineStep;
  stepState: StepState;
}

/** All five steps, always visible: finished work stays on screen when one fails. */
export function PipelineStepper({ status, currentStep, stepState }: PipelineStepperProps) {
  const progress = stepProgress(status, currentStep, stepState);

  return (
    <Card>
      <CardContent className="grid gap-px bg-border p-px sm:grid-cols-5">
        {progress.map(({ step, state }, index) => {
          const Icon = ICONS[state];
          return (
            <div
              key={step}
              aria-current={state === "running" || state === "failed" ? "step" : undefined}
              className="flex flex-col gap-2 bg-card p-4"
            >
              <div className="flex items-center gap-2">
                <Icon
                  aria-hidden="true"
                  className={cn(
                    "h-4 w-4 shrink-0",
                    state === "done" && "text-emerald-600 dark:text-emerald-400",
                    state === "running" && "animate-spin text-primary",
                    state === "failed" && "text-destructive",
                    state === "pending" && "text-muted-foreground",
                  )}
                />
                <span className="font-mono text-xs text-muted-foreground">{index + 1}</span>
              </div>
              <span className="text-sm font-medium leading-tight">{STEP_LABELS[step]}</span>
              <span
                className={cn(
                  "text-xs",
                  state === "failed" ? "text-destructive" : "text-muted-foreground",
                )}
              >
                {STATE_TEXT[state]}
              </span>
            </div>
          );
        })}
      </CardContent>
    </Card>
  );
}
