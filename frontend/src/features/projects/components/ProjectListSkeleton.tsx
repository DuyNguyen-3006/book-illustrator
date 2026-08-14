import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";

/** Matches ProjectCard's shape so nothing shifts when the real rows arrive. */
export function ProjectListSkeleton({ rows = 3 }: { rows?: number }) {
  return (
    <div role="status" aria-label="Loading projects" aria-busy="true" className="flex flex-col gap-4">
      {Array.from({ length: rows }, (_, index) => (
        <Card key={index}>
          <CardHeader className="flex flex-row items-start justify-between gap-4 space-y-0">
            <div className="flex w-full flex-col gap-2">
              <Skeleton className="h-5 w-2/5" />
              <Skeleton className="h-3 w-24" />
            </div>
            <Skeleton className="h-5 w-24 rounded-full" />
          </CardHeader>
          <CardContent className="flex flex-col gap-2">
            <Skeleton className="h-2 w-full rounded-full" />
            <Skeleton className="h-3 w-40" />
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
