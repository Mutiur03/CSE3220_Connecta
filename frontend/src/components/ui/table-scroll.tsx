import type { HTMLAttributes } from "react";
import { tableScrollClasses } from "./classes";

type TableScrollProps = HTMLAttributes<HTMLDivElement> & { label: string };

// Keyboard-reachable, named scroll region for wide tables on narrow screens.
export function TableScroll({ label, className, ...props }: TableScrollProps) {
  return (
    <div
      role="region"
      aria-label={label}
      tabIndex={0}
      className={[tableScrollClasses, className].filter(Boolean).join(" ")}
      {...props}
    />
  );
}
