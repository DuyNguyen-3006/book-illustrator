/**
 * The hero art. Drawn inline rather than fetched: the app is local-only (spec
 * §08), so nothing here may depend on a CDN or a stock-photo host. Line work in
 * the ink colour, one orange element, matching the rest of the palette.
 */
export function ReadingIllustration({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 420 320"
      fill="none"
      className={className}
      role="img"
      aria-label="A stack of books with a bookmark and a pencil"
    >
      <g
        stroke="currentColor"
        strokeWidth="4"
        strokeLinecap="round"
        strokeLinejoin="round"
        vectorEffect="non-scaling-stroke"
      >
        {/* Ground line */}
        <path d="M40 274h340" />

        {/* Book stack, widest at the bottom */}
        <rect x="70" y="238" width="250" height="34" rx="8" />
        <path d="M92 238v34M300 238v34" />
        <rect x="86" y="200" width="226" height="34" rx="8" />
        <path d="M108 200v34" />
        <rect x="104" y="162" width="196" height="34" rx="8" />
        <path d="M126 162v34" />

        {/* Page edges */}
        <path d="M112 249h160M126 211h140M142 173h120" opacity="0.5" />

        {/* Bookmark */}
        <path d="M236 162v58l18-14 18 14v-58" fill="hsl(var(--accent))" stroke="currentColor" />

        {/* Pencil resting against the stack */}
        <path d="M330 120l40 40" />
        <path d="M322 112l16-16 48 48-16 16z" />
        <path d="M322 112l-14 30 30-14z" />

        {/* Loose page */}
        <path d="M60 236l40-14 14 40-40 14z" />
        <path d="M74 240h22M78 250h22" opacity="0.5" />

        {/* Small sprig, echoing the reference's plant */}
        <path d="M356 274v-52" />
        <path d="M356 246c0-14 10-22 22-22M356 232c0-12-9-19-19-19" />
      </g>
    </svg>
  );
}
