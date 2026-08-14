import { useNavigate } from "react-router-dom";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { IdentityForm } from "../components/IdentityForm";

export function IdentityPage() {
  const navigate = useNavigate();

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-md flex-col justify-center gap-8 px-6 py-12">
      <div className="flex flex-col gap-2">
        <h1 className="text-3xl font-semibold tracking-tight">Book Illustrator</h1>
        <p className="text-muted-foreground">
          Turn a book&rsquo;s text into character portraits and a chapter illustration.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Sign in</CardTitle>
          <CardDescription>
            Your email loads your projects. There is no password &mdash; a new email starts a new account.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <IdentityForm onSignedIn={() => navigate("/projects", { replace: true })} />
        </CardContent>
      </Card>
    </main>
  );
}
