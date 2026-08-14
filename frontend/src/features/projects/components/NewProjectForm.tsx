import { useRef, useState, type FormEvent } from "react";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { cn } from "@/lib/utils";
import { messageOf } from "@/shared/lib/errors";
import { useCreateProject } from "../hooks/useCreateProject";

const CONTROL_CLASSES =
  "w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground " +
  "placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 " +
  "focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background " +
  "aria-[invalid=true]:border-destructive";

interface FieldErrors {
  title?: string;
  source?: string;
}

export function NewProjectForm({ onCreated }: { onCreated: (projectId: number) => void }) {
  const [title, setTitle] = useState("");
  const [bookText, setBookText] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const fileInput = useRef<HTMLInputElement>(null);
  const { mutate: create, isPending, error } = useCreateProject();

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const errors: FieldErrors = {};
    if (!title.trim()) {
      errors.title = "Give the project a title.";
    }

    // The backend accepts exactly one source (ProjectController.resolveBookText);
    // catching it here means the user gets a useful message instead of a 400.
    const hasText = bookText.trim().length > 0;
    if (hasText && file) {
      errors.source = "Use one source, not both: clear the pasted text or remove the file.";
    } else if (!hasText && !file) {
      errors.source = "Paste the text or upload a .txt file.";
    } else if (file && !file.name.toLowerCase().endsWith(".txt")) {
      errors.source = "The uploaded file must be a .txt file.";
    }

    setFieldErrors(errors);
    if (errors.title || errors.source) {
      return;
    }

    create(
      { title: title.trim(), bookText: file ? "" : bookText.trim(), file },
      { onSuccess: (project) => onCreated(project.projectId) },
    );
  }

  function clearFile() {
    setFile(null);
    if (fileInput.current) {
      fileInput.current.value = "";
    }
  }

  return (
    <form className="flex flex-col gap-6" onSubmit={handleSubmit} noValidate>
      <div className="flex flex-col gap-2">
        <Label htmlFor="title">Title</Label>
        <input
          id="title"
          name="title"
          className={cn(CONTROL_CLASSES, "h-11")}
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          aria-invalid={Boolean(fieldErrors.title)}
          aria-describedby={fieldErrors.title ? "title-error" : undefined}
        />
        {fieldErrors.title && (
          <p id="title-error" className="text-sm text-destructive">
            {fieldErrors.title}
          </p>
        )}
      </div>

      <div className="flex flex-col gap-2">
        <Label htmlFor="bookText">Paste the book&rsquo;s text</Label>
        <textarea
          id="bookText"
          name="bookText"
          rows={10}
          className={cn(CONTROL_CLASSES, "resize-y font-mono text-xs leading-relaxed")}
          value={bookText}
          onChange={(event) => setBookText(event.target.value)}
          disabled={Boolean(file)}
          aria-describedby="source-help"
        />
      </div>

      <div className="flex flex-col gap-2">
        <Label htmlFor="file">Or upload a .txt file</Label>
        <div className="flex flex-wrap items-center gap-3">
          <input
            id="file"
            name="file"
            type="file"
            accept=".txt,text/plain"
            ref={fileInput}
            className={cn(
              CONTROL_CLASSES,
              "file:mr-3 file:rounded-md file:border-0 file:bg-secondary file:px-3 file:py-1.5 file:text-sm file:text-secondary-foreground",
            )}
            onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            aria-describedby="source-help"
          />
          {file && (
            <Button type="button" variant="ghost" size="sm" onClick={clearFile}>
              Remove file
            </Button>
          )}
        </div>
        <p id="source-help" className="text-xs text-muted-foreground">
          One source per project: paste the text or upload a file.
        </p>
        {fieldErrors.source && <p className="text-sm text-destructive">{fieldErrors.source}</p>}
      </div>

      {error && (
        <Alert role="alert" variant="destructive" size="sm">
          <AlertDescription>{messageOf(error)}</AlertDescription>
        </Alert>
      )}

      <div className="flex items-center gap-3">
        {/* Disabled while in flight: a double click must not create two projects. */}
        <Button type="submit" size="lg" disabled={isPending}>
          {isPending ? "Creating" : "Create project"}
        </Button>
      </div>
    </form>
  );
}
