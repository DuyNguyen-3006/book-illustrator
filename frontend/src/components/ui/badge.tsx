
import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "@/lib/utils"

const badgeVariants = cva(
  "inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2",
  {
    variants: {
      // Same reason as button.tsx: the shipped `badge-3d-*` classes come from
      // Lightswind's plugin and ignore this project's token palette.
      variant: {
        default: "border-transparent bg-primary text-primary-foreground",
        secondary: "border-transparent bg-secondary text-secondary-foreground",
        destructive: "border-transparent bg-destructive text-destructive-foreground",
        outline: "border-border text-foreground",
        success: "border-transparent bg-emerald-600/15 text-emerald-700 dark:text-emerald-400",
        warning: "border-transparent bg-amber-600/15 text-amber-700 dark:text-amber-400",
        info: "border-transparent bg-muted text-muted-foreground",
      },
      size: {
        default: "px-2.5 py-0.5 text-xs",
        sm: "px-2 py-0.5 text-xs",
        lg: "px-3 py-1 text-sm",
      },
      shape: {
        default: "rounded-full",
        square: "rounded-sm",
        rounded: "rounded-md",
      }
    },
    defaultVariants: {
      variant: "default",
      size: "default",
      shape: "default",
    },
  }
)

export interface BadgeProps
  extends React.HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof badgeVariants> {
  withDot?: boolean;
  dotColor?: string;
  interactive?: boolean;
  highlighted?: boolean;
}

function Badge({ 
  className, 
  variant, 
  size,
  shape,
  withDot,
  dotColor = "currentColor",
  interactive,
  highlighted,
  ...props 
}: BadgeProps) {
  return (
    <div 
      className={cn(
        badgeVariants({ variant, size, shape }), 
        interactive && "cursor-pointer hover:opacity-80",
        highlighted && "ring-2 ring-offset-2 ring-ring",
        className
      )} 
      {...props}
    >
      {withDot && (
        <span 
          className="mr-1 h-1.5 w-1.5 rounded-full inline-block" 
          style={{ backgroundColor: dotColor }}
        />
      )}
      {props.children}
    </div>
  )
}

export { Badge, badgeVariants }
