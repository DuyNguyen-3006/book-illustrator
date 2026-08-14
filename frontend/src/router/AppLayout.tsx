import { Link, Outlet } from "react-router-dom";
import { useCurrentUser } from "@/features/auth/hooks/useCurrentUser";

/** Shell for every signed-in screen. Sign out arrives with #25. */
export function AppLayout() {
  const { data: user } = useCurrentUser();

  return (
    <div className="flex min-h-[100dvh] flex-col">
      <header className="border-b border-border">
        <div className="mx-auto flex h-16 w-full max-w-5xl items-center justify-between gap-4 px-6">
          <Link
            to="/projects"
            className="font-mono text-xs uppercase tracking-[0.2em] text-muted-foreground transition-colors hover:text-foreground"
          >
            Book Illustrator
          </Link>
          {user && <span className="truncate text-sm text-muted-foreground">{user.email}</span>}
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl flex-1 px-6 py-10">
        <Outlet />
      </main>
    </div>
  );
}
