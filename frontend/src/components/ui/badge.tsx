import type { HTMLAttributes } from "react";
import { badgeClasses, type BadgeTone } from "./classes";

type BadgeProps = HTMLAttributes<HTMLSpanElement> & {
  tone?: BadgeTone;
  /** A 6px brass dot for a status that needs a look (pending, unread). Never for decoration. */
  dot?: boolean;
};

export function Badge({
  tone = "neutral",
  dot = false,
  className,
  children,
  ...props
}: BadgeProps) {
  return (
    <span
      className={[badgeClasses(tone), className].filter(Boolean).join(" ")}
      {...props}
    >
      {dot && (
        <span
          aria-hidden="true"
          className="mr-1.5 size-1.5 rounded-full bg-highlight"
        />
      )}
      {children}
    </span>
  );
}
