import { Link } from "react-router-dom";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { formatDate } from "@/shared/lib/formatDate";
import type { ProjectSummary } from "../types/project.types";
import { PipelineProgress } from "./PipelineProgress";
import { ProjectStatusBadge } from "./ProjectStatusBadge";

export function ProjectCard({ project }: { project: ProjectSummary }) {
  return (
    <Card className="transition-colors hover:border-primary/40">
      <CardHeader className="flex flex-row items-start justify-between gap-4 space-y-0">
        <div className="min-w-0">
          <CardTitle className="truncate text-base">
            {/* The whole row is one target: title link, no second "Open" button competing with it. */}
            <Link
              to={`/projects/${project.projectId}`}
              className="rounded-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background"
            >
              {project.title}
            </Link>
          </CardTitle>
          <p className="mt-1 text-xs text-muted-foreground">Created {formatDate(project.createdAt)}</p>
        </div>
        <ProjectStatusBadge status={project.status} stepState={project.stepState} />
      </CardHeader>
      <CardContent>
        <PipelineProgress
          status={project.status}
          currentStep={project.currentStep}
          stepState={project.stepState}
        />
      </CardContent>
    </Card>
  );
}
