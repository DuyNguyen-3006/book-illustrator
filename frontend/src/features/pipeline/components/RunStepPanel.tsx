import { useState } from "react";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";
import { STEP_LABELS, STEP_RUNNING_LABELS } from "@/shared/constants/pipeline";
import { messageOf } from "@/shared/lib/errors";
import { useRunStep } from "../hooks/useRunStep";
import type { ProjectDetail } from "../types/pipeline.types";

/**
 * One action for the step the project is actually on. The backend chooses which
 * step runs, so this cannot ask for a step out of order.
 */
export function RunStepPanel({ project }: { project: ProjectDetail }) {
  const [style, setStyle] = useState("");
  const { mutate: run, isPending, error } = useRunStep(project.projectId);

  const running = project.stepState === "RUNNING";
  const finished = project.status === "COMPLETED";
  const failed = project.stepState === "FAILED";
  const stepLabel = STEP_LABELS[project.currentStep];

  if (finished) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="text-base">All five steps are done</CardTitle>
          <CardDescription>The portraits and the chapter illustration are below.</CardDescription>
        </CardHeader>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">
          {running ? STEP_RUNNING_LABELS[project.currentStep] : `Next: ${stepLabel}`}
        </CardTitle>
        <CardDescription>
          {running
            ? "This step is already running. Leaving or reloading will not start it again."
            : "Each step is one call to Gemini and runs only when you ask for it."}
        </CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {project.currentStep === "STYLE" && !running && (
          <div className="flex flex-col gap-2">
            <Label htmlFor="style">Art style (optional)</Label>
            <input
              id="style"
              className={cn(
                "h-11 w-full rounded-md border border-input bg-background px-3 text-sm",
                "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background",
              )}
              placeholder="Leave empty and Gemini will pick one from the book"
              value={style}
              onChange={(event) => setStyle(event.target.value)}
            />
          </div>
        )}

        {error && (
          <Alert role="alert" variant="destructive" size="sm">
            <AlertDescription>{messageOf(error)}</AlertDescription>
          </Alert>
        )}

        <div>
          {/* Disabled while the backend says RUNNING, so a second tab or a double
              click cannot fire the same paid call twice. */}
          <Button size="lg" disabled={running || isPending} onClick={() => run({ style })}>
            {running || isPending ? "Running" : failed ? `Retry ${stepLabel}` : `Run ${stepLabel}`}
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
