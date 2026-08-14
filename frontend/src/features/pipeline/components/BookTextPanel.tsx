import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

/**
 * Spec §4.4: the book text stays readable at any point in the pipeline. It is
 * collapsed by default because a whole book would bury the pipeline controls.
 */
export function BookTextPanel({ bookText }: { bookText: string }) {
  const [expanded, setExpanded] = useState(false);
  const characters = bookText.length.toLocaleString();

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between gap-4 space-y-0">
        <div>
          <CardTitle className="text-base">Book text</CardTitle>
          <p className="mt-1 text-xs text-muted-foreground">{characters} characters</p>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={() => setExpanded((open) => !open)}
          aria-expanded={expanded}
          aria-controls="book-text"
        >
          {expanded ? "Hide" : "Read"}
        </Button>
      </CardHeader>
      {expanded && (
        <CardContent>
          <pre
            id="book-text"
            className="max-h-96 overflow-auto whitespace-pre-wrap rounded-md border border-border bg-muted/40 p-4 font-mono text-xs leading-relaxed"
          >
            {bookText}
          </pre>
        </CardContent>
      )}
    </Card>
  );
}
