import type { HTMLAttributes } from "react";
import { panelClasses } from "./classes";

export function Panel({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={[panelClasses, className].filter(Boolean).join(" ")}
      {...props}
    />
  );
}
