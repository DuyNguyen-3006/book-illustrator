/**
 * Follows the operating system's light/dark setting. The vendored components use
 * Tailwind's `dark:` variants, which need the class on <html>, so a media query
 * alone would leave them half-themed.
 */
export function syncThemeWithSystem(): () => void {
  const query = window.matchMedia("(prefers-color-scheme: dark)");

  const apply = (prefersDark: boolean) => {
    document.documentElement.classList.toggle("dark", prefersDark);
  };

  apply(query.matches);
  const onChange = (event: MediaQueryListEvent) => apply(event.matches);
  query.addEventListener("change", onChange);

  return () => query.removeEventListener("change", onChange);
}
