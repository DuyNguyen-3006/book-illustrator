const FORMATTER = new Intl.DateTimeFormat(undefined, {
  year: "numeric",
  month: "short",
  day: "numeric",
});

/** Backend timestamps are ISO-8601 with an offset; render them in the reader's locale. */
export function formatDate(isoTimestamp: string): string {
  const date = new Date(isoTimestamp);
  return Number.isNaN(date.getTime()) ? "unknown date" : FORMATTER.format(date);
}
