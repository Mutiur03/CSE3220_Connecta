import type { HTMLAttributes } from "react";
import { cardClasses } from "./classes";

export function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={[cardClasses, className].filter(Boolean).join(" ")}
      {...props}
    />
  );
}
