
import * as React from "react";
import { cn } from "@/lib/utils";
import { AlertCircle, CheckCircle, Info, X, AlertTriangle } from "lucide-react";

// Restyled onto this project's tokens: the shipped variants hardcode pure white
// and pure black surfaces and a fixed red, none of which follow the palette.
const alertVariants = {
  variant: {
    default: "bg-card text-card-foreground border-border",
    destructive:
      "border-destructive/40 bg-destructive/5 text-destructive [&>svg]:text-destructive",
    success: "border-emerald-600/40 bg-emerald-600/5 text-emerald-700 dark:text-emerald-400",
    warning: "border-amber-600/40 bg-amber-600/5 text-amber-700 dark:text-amber-400",
    info: "border-border bg-muted text-foreground",
  },
  // Same padding scale as Card, so text inside an alert and text inside a card
  // start on the same left edge instead of two edges 8px apart.
  size: {
    default: "p-6",
    sm: "p-4 text-sm",
    lg: "p-8 text-base"
  }
};

interface AlertProps extends React.HTMLAttributes<HTMLDivElement> {
  /** The style variant of the alert */
  variant?: keyof typeof alertVariants.variant;
  /** The size of the alert */
  size?: keyof typeof alertVariants.size;
  /** Whether the alert should be dismissible */
  dismissible?: boolean;
  /** Callback fired when dismissing the alert */
  onDismiss?: () => void;
  /** Whether to display an icon */
  withIcon?: boolean;
  /** Custom icon to display */
  icon?: React.ReactNode;
}

const Alert = React.forwardRef<HTMLDivElement, AlertProps>(
  ({ 
    className, 
    variant = "default", 
    size = "default", 
    dismissible = false,
    onDismiss,
    withIcon = false,
    icon,
    children,
    ...props 
  }, ref) => {
    // Icon mapping based on variant
    const variantIcons = {
      default: <Info className="h-4 w-4" />,
      destructive: <AlertCircle className="h-4 w-4" />,
      success: <CheckCircle className="h-4 w-4" />,
      warning: <AlertTriangle className="h-4 w-4" />,
      info: <Info className="h-4 w-4" />
    };

    const handleDismiss = () => {
      if (onDismiss) {
        onDismiss();
      }
    };

    return (
      <div
        ref={ref}
        role="alert"
        className={cn(
          "relative w-full rounded-lg border",
          withIcon && "[&>svg]:absolute [&>svg]:left-4 [&>svg]:top-4 [&>svg]:text-foreground",
          withIcon && "[&>svg~*]:pl-7 [&>svg+div]:translate-y-[-3px]",
          alertVariants.variant[variant],
          alertVariants.size[size],
          className
        )}
        {...props}
      >
        {withIcon && (icon || variantIcons[variant])}
        {children}
        {dismissible && (
          <button
            className="absolute top-4 right-4 rounded-full p-1 
            text-foreground/70 opacity-70 
            transition-opacity hover:opacity-100 
            focus:outline-none focus:ring-2 focus:ring-ring 
            focus:ring-offset-2"
            onClick={handleDismiss}
            aria-label="Dismiss alert"
          >
            <X className="h-4 w-4" />
          </button>
        )}
      </div>
    );
  }
);
Alert.displayName = "Alert";

const AlertTitle = React.forwardRef<
  HTMLParagraphElement,
  React.HTMLAttributes<HTMLHeadingElement> & {
    /** Size of the title */
    size?: "sm" | "default" | "lg";
  }
>(({ className, size = "default", ...props }, ref) => {
  const sizeClasses = {
    sm: "text-sm",
    default: "text-base",
    lg: "text-lg"
  };

  return (
    <h5
      ref={ref}
      className={cn("mb-1 font-medium leading-none tracking-tight", sizeClasses[size], className)}
      {...props}
    />
  );
});
AlertTitle.displayName = "AlertTitle";

const AlertDescription = React.forwardRef<
  HTMLParagraphElement,
  React.HTMLAttributes<HTMLParagraphElement> & {
    /** Text color intensity */
    intensity?: "muted" | "default";
  }
>(({ className, intensity = "default", ...props }, ref) => (
  <div
    ref={ref}
    className={cn(
      "text-sm [&_p]:leading-relaxed", 
      intensity === "muted" ? "text-muted-foreground" : "",
      className
    )}
    {...props}
  />
));
AlertDescription.displayName = "AlertDescription";

export { Alert, AlertTitle, AlertDescription };
