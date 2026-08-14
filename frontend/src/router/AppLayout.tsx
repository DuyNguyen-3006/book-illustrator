import { Link, Outlet } from "react-router-dom";
import { SignOutButton } from "@/features/auth/components/SignOutButton";
import { useCurrentUser } from "@/features/auth/hooks/useCurrentUser";

/** Shell for every signed-in screen. */
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
          <div className="flex items-center gap-4">
            {user && <span className="hidden truncate text-sm text-muted-foreground sm:inline">{user.email}</span>}
            <SignOutButton />
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl flex-1 px-6 py-10">
        <Outlet />
      </main>
    </div>
  );
}
