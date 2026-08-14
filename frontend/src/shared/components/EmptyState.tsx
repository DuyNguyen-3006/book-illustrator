import type { ReactNode } from "react";
import { Card, CardContent, CardDescription, CardTitle } from "@/components/ui/card";

interface EmptyStateProps {
  title: string;
  description: string;
  /** The action that fixes the emptiness; an empty state without one is a dead end. */
  action?: ReactNode;
}

export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <Card>
      <CardContent className="flex flex-col items-start gap-3 py-10">
        <CardTitle className="text-base">{title}</CardTitle>
        <CardDescription className="max-w-[52ch]">{description}</CardDescription>
        {action}
      </CardContent>
    </Card>
  );
}
