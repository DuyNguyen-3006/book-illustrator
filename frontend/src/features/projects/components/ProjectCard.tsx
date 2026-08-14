import { Link } from "react-router-dom";
import { ArrowUpRight } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { formatDate } from "@/shared/lib/formatDate";
import type { ProjectSummary } from "../types/project.types";
import { PipelineProgress } from "./PipelineProgress";
import { ProjectStatusBadge } from "./ProjectStatusBadge";

export function ProjectCard({ project }: { project: ProjectSummary }) {
  return (
    <Card className="group relative overflow-hidden transition-colors hover:border-foreground/30">
      <CardContent className="grid gap-6 py-6 sm:grid-cols-[1fr_minmax(0,18rem)] sm:items-center">
        <div className="flex min-w-0 flex-col gap-2">
          <div className="flex items-center gap-3">
            <h3 className="truncate text-xl font-semibold tracking-tight">
              {/* Whole row is one target: the title link stretches over the card. */}
              <Link
                to={`/projects/${project.projectId}`}
                className="rounded-sm after:absolute after:inset-0 after:content-[''] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background"
              >
                {project.title}
              </Link>
            </h3>
            <ArrowUpRight
              aria-hidden="true"
              className="h-4 w-4 shrink-0 text-muted-foreground transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5 group-hover:text-foreground"
            />
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <ProjectStatusBadge status={project.status} stepState={project.stepState} />
            <span className="text-xs text-muted-foreground">Created {formatDate(project.createdAt)}</span>
          </div>
        </div>

        <PipelineProgress
          status={project.status}
          currentStep={project.currentStep}
          stepState={project.stepState}
        />
      </CardContent>
    </Card>
  );
}
