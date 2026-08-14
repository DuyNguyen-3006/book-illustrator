import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { CharacterItem } from "../types/pipeline.types";
import { GeneratedImage } from "./GeneratedImage";

export function CharacterCard({ character }: { character: CharacterItem }) {
  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="text-base">{character.name}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-3">
        <GeneratedImage
          url={character.portraitUrl}
          alt={`Portrait of ${character.name}`}
          pendingLabel="Portrait not generated yet"
        />
        <p className="text-sm leading-relaxed text-muted-foreground">{character.prompt}</p>
      </CardContent>
    </Card>
  );
}
