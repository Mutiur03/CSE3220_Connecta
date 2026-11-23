import type { SelectHTMLAttributes } from "react";
import { fieldClasses, fieldSmClasses } from "./classes";

// A native <select>: keyboard, form value and accessibility stay the browser's. Where the
// browser supports customizable selects, globals.css (.select) restyles the button and the
// picker to the design tokens. Elsewhere it stays a plain field-styled select.
export function Select({
  controlSize = "md",
  className,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & {
  /** "sm" (32px) inside a table row, next to size="sm" buttons. */
  controlSize?: "md" | "sm";
}) {
  return (
    <select
      className={[
        "select",
        controlSize === "sm" ? fieldSmClasses : fieldClasses,
        className,
      ]
        .filter(Boolean)
        .join(" ")}
      {...props}
    />
  );
}
