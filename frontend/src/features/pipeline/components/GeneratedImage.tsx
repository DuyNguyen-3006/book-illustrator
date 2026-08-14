import { cn } from "@/lib/utils";

interface GeneratedImageProps {
  /** Null until the backend has actually generated and stored the image. */
  url: string | null;
  alt: string;
  pendingLabel: string;
  className?: string;
}

/**
 * A missing image is a state, not a broken <img>: until the step runs there is
 * nothing to load, and saying so beats a browser's broken-image icon.
 */
export function GeneratedImage({ url, alt, pendingLabel, className }: GeneratedImageProps) {
  if (!url) {
    return (
      <div
        className={cn(
          "flex aspect-square items-center justify-center rounded-md border border-dashed border-border bg-muted/40 p-4 text-center text-xs text-muted-foreground",
          className,
        )}
      >
        {pendingLabel}
      </div>
    );
  }

  return (
    <img
      // Same-origin API path, proxied to the backend, so the session cookie rides along.
      src={`/api${url}`}
      alt={alt}
      loading="lazy"
      className={cn("aspect-square w-full rounded-md border border-border object-cover", className)}
    />
  );
}
