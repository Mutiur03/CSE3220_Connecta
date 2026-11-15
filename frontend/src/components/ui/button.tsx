import type { ButtonHTMLAttributes } from "react";
import { buttonClasses, type ButtonTone } from "./classes";

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonTone;
};

export function Button({
  variant = "secondary",
  className,
  ...props
}: ButtonProps) {
  return (
    <button
      className={[buttonClasses(variant), className].filter(Boolean).join(" ")}
      {...props}
    />
  );
}
