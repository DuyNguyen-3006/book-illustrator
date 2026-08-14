import { Link, useNavigate } from "react-router-dom";
import { Card, CardContent } from "@/components/ui/card";
import { ReadingIllustration } from "@/features/landing/components/ReadingIllustration";
import { IdentityForm } from "../components/IdentityForm";

export function IdentityPage() {
  const navigate = useNavigate();

  return (
    <div className="mx-auto flex min-h-[100dvh] w-full max-w-6xl flex-col px-6">
      <header className="flex h-20 items-center">
        <Link to="/" className="text-lg font-bold tracking-tight">
          Book Illustrator
        </Link>
      </header>

      <main className="grid flex-1 items-center gap-12 py-10 lg:grid-cols-[1fr_minmax(0,26rem)] lg:py-16">
        <div className="hidden flex-col gap-8 lg:flex">
          <h1 className="max-w-[14ch] text-4xl font-bold leading-[1.05] tracking-tight xl:text-5xl">
            Sign in and pick up where the pipeline stopped.
          </h1>
          <ReadingIllustration className="w-full max-w-md text-foreground" />
        </div>

        <Card>
          <CardContent className="flex flex-col gap-6 py-8">
            <div className="flex flex-col gap-2">
              <h2 className="text-2xl font-bold tracking-tight">Sign in</h2>
              <p className="text-sm leading-relaxed text-muted-foreground">
                Your email loads your projects. There is no password: a new email starts a new
                account.
              </p>
            </div>
            <IdentityForm onSignedIn={() => navigate("/projects", { replace: true })} />
          </CardContent>
        </Card>
      </main>
    </div>
  );
}
