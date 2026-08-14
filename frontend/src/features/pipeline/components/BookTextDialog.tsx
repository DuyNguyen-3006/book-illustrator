import { useEffect, useRef } from "react";
import { X } from "lucide-react";
import { Button } from "@/components/ui/button";

/**
 * Native <dialog>: modal semantics, focus trap, Escape and the backdrop all come
 * from the platform, so this needs no dialog library (CLAUDE.md §2.1).
 */
export function BookTextDialog({
  bookText,
  open,
  onClose,
}: {
  bookText: string;
  open: boolean;
  onClose: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const element = dialog.current;
    if (!element) {
      return;
    }
    // jsdom does not implement showModal, so tests fall back to the plain open
    // attribute; browsers get the real modal, with the focus trap and Escape.
    if (open && !element.open) {
      if (typeof element.showModal === "function") {
        element.showModal();
      } else {
        element.setAttribute("open", "");
      }
    }
    if (!open && element.open) {
      if (typeof element.close === "function") {
        element.close();
      } else {
        element.removeAttribute("open");
      }
    }
  }, [open]);

  return (
    <dialog
      ref={dialog}
      onClose={onClose}
      aria-labelledby="book-text-title"
      className="w-[min(56rem,92vw)] rounded-lg border border-border bg-card p-0 text-card-foreground backdrop:bg-foreground/40"
    >
      <div className="flex items-center justify-between gap-4 border-b border-border px-6 py-4">
        <div>
          <h2 id="book-text-title" className="text-base font-semibold tracking-tight">
            Book text
          </h2>
          <p className="mt-1 text-xs text-muted-foreground">
            {bookText.length.toLocaleString()} characters
          </p>
        </div>
        <Button variant="ghost" size="icon" onClick={onClose} aria-label="Close">
          <X aria-hidden="true" className="h-4 w-4" />
        </Button>
      </div>

      <pre className="max-h-[70vh] overflow-auto whitespace-pre-wrap px-6 py-5 font-mono text-xs leading-relaxed">
        {bookText}
      </pre>
    </dialog>
  );
}
