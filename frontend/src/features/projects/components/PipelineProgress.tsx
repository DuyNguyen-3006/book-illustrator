import { Progress } from "@/components/ui/progress";
import { STEP_LABELS, STEP_RUNNING_LABELS } from "@/shared/constants/pipeline";
import { completedStepCount, stepProgress } from "@/shared/lib/projectProgress";
import { PIPELINE_STEPS, type PipelineStep, type ProjectStatus, type StepState } from "@/shared/types/pipeline.types";

interface PipelineProgressProps {
  status: ProjectStatus;
  currentStep: PipelineStep;
  stepState: StepState;
}

/**
 * The bar fills from real completed-step counts, not an estimated percentage
 * (frontend-rules §3); the line underneath names the step the project sits on.
 */
export function PipelineProgress({ status, currentStep, stepState }: PipelineProgressProps) {
  const progress = stepProgress(status, currentStep, stepState);
  const done = completedStepCount(progress);
  const active = progress.find((entry) => entry.state === "running" || entry.state === "failed");

  return (
    <div className="flex flex-col gap-2">
      <Progress
        value={done}
        max={PIPELINE_STEPS.length}
        size="sm"
        color={stepState === "FAILED" ? "danger" : "primary"}
        aria-label={`${done} of ${PIPELINE_STEPS.length} steps done`}
      />
      <p className="text-xs text-muted-foreground">
        {done} of {PIPELINE_STEPS.length} steps done
        {active ? ` · ${activeLabel(active.state, active.step)}` : ""}
      </p>
    </div>
  );
}

function activeLabel(state: string, step: PipelineStep): string {
  return state === "running" ? STEP_RUNNING_LABELS[step] : `${STEP_LABELS[step]} failed`;
}
