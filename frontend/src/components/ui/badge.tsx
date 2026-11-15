import type { HTMLAttributes } from "react";
import { badgeClasses, type BadgeTone } from "./classes";

type BadgeProps = HTMLAttributes<HTMLSpanElement> & { tone?: BadgeTone };

export function Badge({ tone = "neutral", className, ...props }: BadgeProps) {
  return (
    <span
      className={[badgeClasses(tone), className].filter(Boolean).join(" ")}
      {...props}
    />
  );
}
