import { Link, useParams } from "react-router-dom";
import { ArrowLeft } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { ErrorState } from "@/shared/components/ErrorState";
import { formatDate } from "@/shared/lib/formatDate";
import { BookTextPanel } from "../components/BookTextPanel";
import { ChapterCard } from "../components/ChapterCard";
import { CharacterCard } from "../components/CharacterCard";
import { PipelineStepper } from "../components/PipelineStepper";
import { RunStepPanel } from "../components/RunStepPanel";
import { StepStatusBanner } from "../components/StepStatusBanner";
import { useProject } from "../hooks/useProject";

export function ProjectDetailPage() {
  const { projectId } = useParams();
  const { data: project, isPending, isFetching, isError, error, refetch } = useProject(Number(projectId));

  if (isPending) {
    return (
      <div role="status" aria-label="Loading project" aria-busy="true" className="flex flex-col gap-8">
        <Skeleton className="h-9 w-72" />
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-48 w-full" />
      </div>
    );
  }

  if (isError) {
    return <ErrorState error={error} onRetry={() => void refetch()} retrying={isFetching} />;
  }

  return (
    <div className="flex flex-col gap-10">
      <div className="flex flex-col gap-3">
        <Link
          to="/projects"
          className="inline-flex w-fit items-center gap-2 text-sm text-muted-foreground transition-colors hover:text-foreground"
        >
          <ArrowLeft aria-hidden="true" className="h-4 w-4" />
          Back to projects
        </Link>
        <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">{project.title}</h1>
        <p className="text-sm text-muted-foreground">Created {formatDate(project.createdAt)}</p>
      </div>

      <PipelineStepper
        status={project.status}
        currentStep={project.currentStep}
        stepState={project.stepState}
      />

      <StepStatusBanner project={project} />

      <RunStepPanel project={project} />

      {project.style && (
        <section className="flex flex-col gap-4">
          <SectionHeading>Art style</SectionHeading>
          <Card>
            <CardContent className="py-6">
              <p className="max-w-[70ch] text-sm leading-relaxed text-muted-foreground">{project.style}</p>
            </CardContent>
          </Card>
        </section>
      )}

      {project.characters.length > 0 && (
        <section className="flex flex-col gap-4">
          <SectionHeading>Characters</SectionHeading>
          <div className="grid gap-4 sm:grid-cols-2">
            {project.characters.map((character) => (
              <CharacterCard key={character.id} character={character} />
            ))}
          </div>
        </section>
      )}

      {project.chapters.length > 0 && (
        <section className="flex flex-col gap-4">
          <SectionHeading>Chapter</SectionHeading>
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

function SectionHeading({ children }: { children: string }) {
  return <h2 className="text-xl font-semibold tracking-tight">{children}</h2>;
}
