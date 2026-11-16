import type { SelectHTMLAttributes } from "react";
import { fieldClasses } from "./classes";

// A native <select>: keyboard, form value and accessibility stay the browser's. Where the
// browser supports customizable selects, globals.css (.select) restyles the button and the
// picker to the design tokens. Elsewhere it stays a plain field-styled select.
export function Select({
  className,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select
      className={["select", fieldClasses, className].filter(Boolean).join(" ")}
      {...props}
    />
  );
}
