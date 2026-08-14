import { Link } from "react-router-dom";
import { Button } from "@/components/ui/button";

const SECTIONS = [
  { href: "#how-it-works", label: "How it works" },
  { href: "#pipeline", label: "The five steps" },
  { href: "#cost", label: "What it costs" },
];

export function LandingNav() {
  return (
    <header className="border-b border-border/70">
      <nav className="mx-auto flex h-20 w-full max-w-6xl items-center justify-between gap-6 px-6">
        <Link to="/" className="text-lg font-bold tracking-tight">
          Book Illustrator
        </Link>

        <div className="hidden items-center gap-8 md:flex">
          {SECTIONS.map((section) => (
            <a
              key={section.href}
              href={section.href}
              className="text-sm text-muted-foreground transition-colors hover:text-foreground"
            >
              {section.label}
            </a>
          ))}
        </div>

        <Button asChild variant="outline" size="sm">
          <Link to="/signin">Sign in</Link>
        </Button>
      </nav>
    </header>
  );
}
