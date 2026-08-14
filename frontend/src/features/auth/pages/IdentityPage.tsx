import { useNavigate } from "react-router-dom";
import { PIPELINE_STEPS } from "@/shared/types/pipeline.types";
import { STEP_LABELS } from "@/shared/constants/pipeline";
import { IdentityForm } from "../components/IdentityForm";

export function IdentityPage() {
  const navigate = useNavigate();

  return (
    <div className="grid min-h-[100dvh] grid-cols-1 lg:grid-cols-[1.15fr_1fr]">
      <section className="flex flex-col justify-between gap-16 border-b border-border px-6 py-12 sm:px-10 lg:border-b-0 lg:border-r lg:py-16 lg:pl-16">
        <div className="flex flex-col gap-6">
          <p className="font-mono text-xs uppercase tracking-[0.2em] text-muted-foreground">
            Book Illustrator
          </p>
          <h1 className="max-w-[14ch] text-4xl font-semibold leading-[1.05] tracking-tight sm:text-5xl lg:text-6xl">
            A book&rsquo;s text, illustrated one step at a time.
          </h1>
          <p className="max-w-[52ch] text-base leading-relaxed text-muted-foreground">
            Paste a book, then run each step yourself. Every result is saved as it lands, so you can
            close the tab and pick the pipeline up where you left it.
          </p>
        </div>

        <ol className="grid gap-px overflow-hidden rounded-lg border border-border bg-border sm:grid-cols-2 lg:max-w-xl">
          {PIPELINE_STEPS.map((step, index) => (
            <li key={step} className="flex items-baseline gap-3 bg-background px-4 py-4 last:sm:col-span-2">
              <span className="font-mono text-xs text-muted-foreground">{index + 1}</span>
              <span className="text-sm font-medium">{STEP_LABELS[step]}</span>
            </li>
          ))}
        </ol>
      </section>

      <main className="flex items-center px-6 py-12 sm:px-10 lg:py-16 lg:pr-16">
        <div className="w-full max-w-sm">
          <h2 className="text-2xl font-semibold tracking-tight">Sign in</h2>
          <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
            Your email loads your projects. There is no password: a new email starts a new account.
          </p>
          <div className="mt-8">
            <IdentityForm onSignedIn={() => navigate("/projects", { replace: true })} />
          </div>
        </div>
      </main>
    </div>
  );
}
