import { Badge } from "@/components/ui/badge";
import { PROJECT_STATUS_LABELS } from "@/shared/constants/pipeline";
import type { ProjectStatus, StepState } from "@/shared/types/pipeline.types";

const VARIANT_BY_STATUS = {
  DRAFT: "outline",
  RUNNING: "default",
  COMPLETED: "success",
} as const;

interface ProjectStatusBadgeProps {
  status: ProjectStatus;
  stepState: StepState;
}

export function ProjectStatusBadge({ status, stepState }: ProjectStatusBadgeProps) {
  // A failed step is the more useful thing to say about a project than "in progress".
  if (stepState === "FAILED") {
    return <Badge variant="destructive">Needs a retry</Badge>;
  }

  return <Badge variant={VARIANT_BY_STATUS[status]}>{PROJECT_STATUS_LABELS[status]}</Badge>;
}
