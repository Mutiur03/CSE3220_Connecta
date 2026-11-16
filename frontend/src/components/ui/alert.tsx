import type { HTMLAttributes } from "react";
import { alertClasses, type AlertTone } from "./classes";

type AlertProps = HTMLAttributes<HTMLDivElement> & { tone?: AlertTone };

// Danger is announced at once (role="alert"); other tones politely (role="status").
export function Alert({ tone = "info", className, ...props }: AlertProps) {
  return (
    <div
      role={tone === "danger" ? "alert" : "status"}
      className={[alertClasses(tone), className].filter(Boolean).join(" ")}
      {...props}
    />
  );
}
