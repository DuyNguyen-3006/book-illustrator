import { STEP_LABELS, STEP_RUNNING_LABELS } from "@/shared/constants/pipeline";
import { completedStepCount, stepProgress, type StepProgressState } from "@/shared/lib/projectProgress";
import { cn } from "@/lib/utils";
import { PIPELINE_STEPS, type PipelineStep, type ProjectStatus, type StepState } from "@/shared/types/pipeline.types";

interface PipelineProgressProps {
  status: ProjectStatus;
  currentStep: PipelineStep;
  stepState: StepState;
}

const SEGMENT_CLASSES: Record<StepProgressState, string> = {
  done: "bg-foreground",
  running: "bg-accent",
  failed: "bg-destructive",
  pending: "bg-border",
};

/**
 * Five segments, one per step, each showing that step's real state. Nothing here
 * is an estimated percentage (frontend-rules §3).
 */
export function PipelineProgress({ status, currentStep, stepState }: PipelineProgressProps) {
  const progress = stepProgress(status, currentStep, stepState);
  const done = completedStepCount(progress);
  const active = progress.find((entry) => entry.state === "running" || entry.state === "failed");

  return (
    <div className="flex flex-col gap-2">
      <div
        className="flex gap-1"
        role="img"
        aria-label={`${done} of ${PIPELINE_STEPS.length} steps done`}
      >
        {progress.map(({ step, state }) => (
          <span
            key={step}
            title={STEP_LABELS[step]}
            className={cn(
              "h-1.5 flex-1 rounded-full transition-colors",
              SEGMENT_CLASSES[state],
              state === "running" && "animate-pulse",
            )}
          />
        ))}
      </div>
      <p className="text-xs text-muted-foreground">
        {done} of {PIPELINE_STEPS.length} steps done
        {active
          ? ` · ${active.state === "running" ? STEP_RUNNING_LABELS[active.step] : `${STEP_LABELS[active.step]} failed`}`
          : ""}
      </p>
    </div>
  );
}
