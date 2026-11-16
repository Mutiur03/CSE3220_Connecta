// Stable API: org components import these names. Re-implemented on the shared
// tokens (globals.css) through ui/classes.ts. Keep the exports and signatures.
import { buttonClasses, fieldClasses } from "./ui/classes";

export const fieldClass = fieldClasses;

export const buttonClass = (
  tone: "primary" | "neutral" | "danger" = "neutral",
) => buttonClasses(tone === "neutral" ? "secondary" : tone);

export const tableClass =
  "w-full min-w-[32rem] text-left text-sm tabular-nums text-primary";
