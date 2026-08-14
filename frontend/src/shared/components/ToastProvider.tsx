import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { CircleCheck, Info, TriangleAlert, X } from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * A small local toast, rather than the vendored one: that component needs
 * framer-motion, which this project removed in #39, and transient feedback does
 * not justify pulling an animation library back in (CLAUDE.md §2.1).
 *
 * Toasts carry transient outcomes only. Persisted state - a failed step, a
 * screen that could not load, a rejected form field - stays on the page, since a
 * message that disappears cannot satisfy "the failure is still there after a
 * reload" (spec §4.3, frontend-rules §1).
 */

export type ToastTone = "success" | "error" | "info";

interface Toast {
  id: number;
  tone: ToastTone;
  message: string;
}

interface ToastApi {
  success: (message: string) => void;
  error: (message: string) => void;
  info: (message: string) => void;
}

const ToastContext = createContext<ToastApi | null>(null);

const VISIBLE_MS = 5000;

const TONE_CLASSES: Record<ToastTone, string> = {
  success: "border-emerald-600/40 bg-card text-foreground",
  error: "border-destructive/50 bg-card text-foreground",
  info: "border-border bg-card text-foreground",
};

const TONE_ICONS: Record<ToastTone, typeof Info> = {
  success: CircleCheck,
  error: TriangleAlert,
  info: Info,
};

const ICON_CLASSES: Record<ToastTone, string> = {
  success: "text-emerald-600 dark:text-emerald-400",
  error: "text-destructive",
  info: "text-muted-foreground",
};

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(1);
  const timers = useRef<number[]>([]);

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const push = useCallback(
    (tone: ToastTone, message: string) => {
      const id = nextId.current++;
      setToasts((current) => [...current, { id, tone, message }]);
      timers.current.push(window.setTimeout(() => dismiss(id), VISIBLE_MS));
    },
    [dismiss],
  );

  // Nothing should keep firing after the tree is gone.
  useEffect(() => () => timers.current.forEach(window.clearTimeout), []);

  const api = useMemo<ToastApi>(
    () => ({
      success: (message) => push("success", message),
      error: (message) => push("error", message),
      info: (message) => push("info", message),
    }),
    [push],
  );

  return (
    <ToastContext.Provider value={api}>
      {children}

      <div
        aria-live="polite"
        aria-label="Notifications"
        className="pointer-events-none fixed inset-x-0 bottom-0 z-50 flex flex-col items-center gap-2 p-4 sm:inset-x-auto sm:right-0 sm:items-end"
      >
        {toasts.map((toast) => {
          const Icon = TONE_ICONS[toast.tone];
          return (
            <div
              key={toast.id}
              role={toast.tone === "error" ? "alert" : "status"}
              className={cn(
                "pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-lg border px-4 py-3 shadow-lg",
                TONE_CLASSES[toast.tone],
              )}
            >
              <Icon aria-hidden="true" className={cn("mt-0.5 h-4 w-4 shrink-0", ICON_CLASSES[toast.tone])} />
              <p className="flex-1 text-sm leading-relaxed">{toast.message}</p>
              <button
                type="button"
                onClick={() => dismiss(toast.id)}
                aria-label="Dismiss notification"
                className="rounded-sm text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <X aria-hidden="true" className="h-4 w-4" />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastApi {
  const api = useContext(ToastContext);
  if (!api) {
    throw new Error("useToast must be used inside a ToastProvider");
  }
  return api;
}
