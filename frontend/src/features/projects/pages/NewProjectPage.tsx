import { Link, useNavigate } from "react-router-dom";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { NewProjectForm } from "../components/NewProjectForm";

export function NewProjectPage() {
  const navigate = useNavigate();

  return (
    <div className="flex flex-col gap-8">
      <div>
        <Link to="/projects" className="text-sm text-muted-foreground hover:text-foreground">
          Back to projects
        </Link>
        <h1 className="mt-2 text-2xl font-semibold tracking-tight">New project</h1>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">The book</CardTitle>
          <CardDescription>
            The text is sent to Gemini once and reused across all five steps, so paste the whole
            book rather than a summary.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <NewProjectForm onCreated={(projectId) => navigate(`/projects/${projectId}`)} />
        </CardContent>
      </Card>
    </div>
  );
}
