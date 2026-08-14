import { Link, useParams } from "react-router-dom";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { ErrorState } from "@/shared/components/ErrorState";
import { formatDate } from "@/shared/lib/formatDate";
import { BookTextPanel } from "../components/BookTextPanel";
import { ChapterCard } from "../components/ChapterCard";
import { CharacterCard } from "../components/CharacterCard";
import { PipelineStepper } from "../components/PipelineStepper";
import { RunStepPanel } from "../components/RunStepPanel";
import { useProject } from "../hooks/useProject";

export function ProjectDetailPage() {
  const { projectId } = useParams();
  const { data: project, isPending, isFetching, isError, error, refetch } = useProject(Number(projectId));

  if (isPending) {
    return (
      <div role="status" aria-label="Loading project" aria-busy="true" className="flex flex-col gap-6">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-28 w-full" />
        <Skeleton className="h-40 w-full" />
      </div>
    );
  }

  if (isError) {
    return <ErrorState error={error} onRetry={() => void refetch()} retrying={isFetching} />;
  }

  return (
    <div className="flex flex-col gap-8">
      <div>
        <Link to="/projects" className="text-sm text-muted-foreground hover:text-foreground">
          Back to projects
        </Link>
        <h1 className="mt-2 text-2xl font-semibold tracking-tight">{project.title}</h1>
        <p className="mt-1 text-sm text-muted-foreground">Created {formatDate(project.createdAt)}</p>
      </div>

      <PipelineStepper
        status={project.status}
        currentStep={project.currentStep}
        stepState={project.stepState}
      />

      <RunStepPanel project={project} />

      {project.style && (
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base">Art style</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-sm leading-relaxed text-muted-foreground">{project.style}</p>
          </CardContent>
        </Card>
      )}

      {project.characters.length > 0 && (
        <section className="flex flex-col gap-4">
          <h2 className="text-lg font-semibold tracking-tight">Characters</h2>
          <div className="grid gap-4 sm:grid-cols-2">
            {project.characters.map((character) => (
              <CharacterCard key={character.id} character={character} />
            ))}
          </div>
        </section>
      )}

      {project.chapters.length > 0 && (
        <section className="flex flex-col gap-4">
          <h2 className="text-lg font-semibold tracking-tight">Chapter</h2>
          <div className="flex flex-col gap-4">
            {project.chapters.map((chapter) => (
              <ChapterCard key={chapter.id} chapter={chapter} />
            ))}
          </div>
        </section>
      )}

      <BookTextPanel bookText={project.bookText} />
    </div>
  );
}
