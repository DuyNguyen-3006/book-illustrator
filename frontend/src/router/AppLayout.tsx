import { Link, NavLink, Outlet } from "react-router-dom";
import { SignOutButton } from "@/features/auth/components/SignOutButton";
import { useCurrentUser } from "@/features/auth/hooks/useCurrentUser";
import { cn } from "@/lib/utils";

/** Shell for every signed-in screen, matching the landing page's header. */
export function AppLayout() {
  const { data: user } = useCurrentUser();

  return (
    <div className="flex min-h-[100dvh] flex-col">
      <header className="border-b border-border/70">
        <div className="mx-auto flex h-20 w-full max-w-6xl items-center justify-between gap-6 px-6">
          <div className="flex items-center gap-8">
            <Link to="/projects" className="text-lg font-bold tracking-tight">
              Book Illustrator
            </Link>
            <NavLink
              to="/projects"
              end
              className={({ isActive }) =>
                cn(
                  "hidden text-sm transition-colors sm:inline",
                  isActive ? "text-foreground" : "text-muted-foreground hover:text-foreground",
                )
              }
            >
              Projects
            </NavLink>
          </div>

          <div className="flex items-center gap-4">
            {user && (
              <span className="hidden max-w-[18rem] truncate text-sm text-muted-foreground md:inline">
                {user.email}
              </span>
            )}
            <SignOutButton />
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-6 py-12">
        <Outlet />
      </main>
    </div>
  );
}
