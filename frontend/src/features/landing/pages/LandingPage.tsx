import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { STEP_LABELS } from "@/shared/constants/pipeline";
import { PIPELINE_STEPS } from "@/shared/types/pipeline.types";
import { LandingNav } from "../components/LandingNav";
import { ReadingIllustration } from "../components/ReadingIllustration";

const STEP_BLURBS: Record<(typeof PIPELINE_STEPS)[number], string> = {
  STYLE: "Pick the art style yourself, or let the model read the book and propose one.",
  CHARACTERS: "Two adult characters, each with the image prompt that will draw them.",
  PORTRAITS: "One portrait per character, generated from that prompt and the style.",
  CHAPTERS: "A chapter illustration prompt that references the characters by name.",
  ILLUSTRATIONS: "The chapter scene, reusing the portraits so faces stay consistent.",
};

export function LandingPage() {
  return (
    <div className="flex min-h-[100dvh] flex-col">
      <LandingNav />

      <main className="flex-1">
        <section className="mx-auto grid w-full max-w-6xl items-center gap-10 px-6 py-16 lg:grid-cols-[1.05fr_1fr] lg:py-24">
          <div className="flex flex-col gap-8">
            <h1 className="max-w-[16ch] text-4xl font-bold leading-[1.05] tracking-tight sm:text-5xl lg:text-6xl">
              Turn a book into portraits and a chapter illustration.
            </h1>
            <p className="max-w-[46ch] text-base leading-relaxed text-muted-foreground">
              Paste the text, then run five steps at your own pace. Nothing runs on its own and
              every result is saved the moment it lands.
            </p>
            <div className="flex flex-wrap items-center gap-4">
              <Button asChild size="lg">
                <Link to="/signin">Get started</Link>
              </Button>
              <span className="text-sm text-muted-foreground">Email and a name. No password.</span>
            </div>
          </div>

          <ReadingIllustration className="w-full text-foreground" />
        </section>

        <section id="how-it-works" className="border-t border-border/70 bg-card/60">
          <div className="mx-auto w-full max-w-6xl px-6 py-16 lg:py-20">
            <h2 className="max-w-[20ch] text-2xl font-bold tracking-tight sm:text-3xl">
              You drive it. The model does the drawing.
            </h2>
            <div className="mt-10 grid gap-6 md:grid-cols-3">
              <Card>
                <CardContent className="flex flex-col gap-2 py-8">
                  <h3 className="text-base font-semibold">One step at a time</h3>
                  <p className="text-sm leading-relaxed text-muted-foreground">
                    Every step waits for you to press the button, so a run costs exactly what you
                    asked for and nothing more.
                  </p>
                </CardContent>
              </Card>
              <Card>
                <CardContent className="flex flex-col gap-2 py-8">
                  <h3 className="text-base font-semibold">Close the tab whenever</h3>
                  <p className="text-sm leading-relaxed text-muted-foreground">
                    Progress lives on the server. Reopen the project and it carries on from where it
                    actually got to, not from the start.
                  </p>
                </CardContent>
              </Card>
              <Card>
                <CardContent className="flex flex-col gap-2 py-8">
                  <h3 className="text-base font-semibold">A failure is not the end</h3>
                  <p className="text-sm leading-relaxed text-muted-foreground">
                    If a step fails you see why, and you retry that step alone. Everything generated
                    before it stays put.
                  </p>
                </CardContent>
              </Card>
            </div>
          </div>
        </section>

        <section id="pipeline" className="mx-auto w-full max-w-6xl px-6 py-16 lg:py-20">
          <h2 className="text-2xl font-bold tracking-tight sm:text-3xl">The five steps</h2>
          <ol className="mt-10 flex flex-col divide-y divide-border border-y border-border">
            {PIPELINE_STEPS.map((step, index) => (
              <li key={step} className="grid gap-2 py-6 sm:grid-cols-[3rem_14rem_1fr] sm:items-baseline">
                <span className="font-mono text-sm text-accent">{String(index + 1).padStart(2, "0")}</span>
                <span className="text-lg font-semibold tracking-tight">{STEP_LABELS[step]}</span>
                <span className="text-sm leading-relaxed text-muted-foreground">{STEP_BLURBS[step]}</span>
              </li>
            ))}
          </ol>
        </section>

        <section id="cost" className="border-t border-border/70 bg-card/60">
          <div className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-6 py-16 lg:flex-row lg:items-center lg:justify-between lg:py-20">
            <div className="flex flex-col gap-3">
              <h2 className="max-w-[24ch] text-2xl font-bold tracking-tight sm:text-3xl">
                Two characters, one chapter, per project.
              </h2>
              <p className="max-w-[52ch] text-sm leading-relaxed text-muted-foreground">
                The caps are enforced on the server, so a project has a predictable ceiling on what
                it can spend at the model.
              </p>
            </div>
            <Button asChild size="lg">
              <Link to="/signin">Start a project</Link>
            </Button>
          </div>
        </section>
      </main>

      <footer className="border-t border-border/70">
        <div className="mx-auto flex w-full max-w-6xl flex-wrap items-center justify-between gap-4 px-6 py-8 text-sm text-muted-foreground">
          <span>Book Illustrator</span>
          <span>Runs locally against your own Gemini key.</span>
        </div>
      </footer>
    </div>
  );
}
