import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { EmptyState } from "@/shared/components/EmptyState";
import { ErrorState } from "@/shared/components/ErrorState";
import { ProjectCard } from "../components/ProjectCard";
import { ProjectListSkeleton } from "../components/ProjectListSkeleton";
import { useProjects } from "../hooks/useProjects";

export function ProjectListPage() {
  const { data: projects, isPending, isFetching, isError, error, refetch } = useProjects();
  // With no projects the empty state owns the primary action, so the header does
  // not offer a second button with exactly the same intent.
  const showHeaderAction = Boolean(projects && projects.length > 0);

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Your projects</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Each project runs the five steps in order, at your pace.
          </p>
        </div>
        {showHeaderAction && (
          <Button asChild>
            <Link to="/projects/new">New project</Link>
          </Button>
        )}
      </div>

      {isPending ? (
        <ProjectListSkeleton />
      ) : isError ? (
        <ErrorState error={error} onRetry={() => void refetch()} retrying={isFetching} />
      ) : projects.length === 0 ? (
        <EmptyState
          title="No projects yet"
          description="Start by pasting a book's text or uploading a .txt file. You can run the first step straight after."
          action={
            <Button asChild>
              <Link to="/projects/new">New project</Link>
            </Button>
          }
        />
      ) : (
        <ul className="flex flex-col gap-4">
          {projects.map((project) => (
            <li key={project.projectId}>
              <ProjectCard project={project} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
