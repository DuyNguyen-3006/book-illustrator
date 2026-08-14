import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { ChapterItem } from "../types/pipeline.types";
import { GeneratedImage } from "./GeneratedImage";

export function ChapterCard({ chapter }: { chapter: ChapterItem }) {
  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="text-base">{chapter.name}</CardTitle>
      </CardHeader>
      <CardContent className="grid gap-4 sm:grid-cols-[minmax(0,220px)_1fr]">
        <GeneratedImage
          url={chapter.illustrationUrl}
          alt={`Illustration for ${chapter.name}`}
          pendingLabel="Illustration not generated yet"
        />
        <p className="text-sm leading-relaxed text-muted-foreground">{chapter.prompt}</p>
      </CardContent>
    </Card>
  );
}
