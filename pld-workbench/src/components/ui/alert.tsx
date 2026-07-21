import { type VariantProps, cva } from "class-variance-authority";
import * as React from "react";
import { cn } from "@/lib/utils";

const alertVariants = cva("relative w-full rounded-lg border p-3 text-sm", {
  variants: {
    variant: {
      default: "bg-background text-foreground",
      destructive: "border-destructive/50 text-destructive",
      warning: "border-amber-200 bg-amber-50 text-amber-950"
    }
  },
  defaultVariants: {
    variant: "default"
  }
});

export const Alert = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement> & VariantProps<typeof alertVariants>>(
  ({ className, variant, ...props }, ref) => <div ref={ref} role="alert" className={cn(alertVariants({ variant }), className)} {...props} />
);
Alert.displayName = "Alert";
