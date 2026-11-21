import type { ButtonHTMLAttributes } from "react";
import { buttonClasses, type ButtonSize, type ButtonTone } from "./classes";

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonTone;
  size?: ButtonSize;
  /** Fill the row. Sign-in style forms use it; everywhere else a button is as wide as its label. */
  block?: boolean;
};

export function Button({
  variant = "secondary",
  size = "md",
  block = false,
  className,
  ...props
}: ButtonProps) {
  return (
    <button
      className={[
        buttonClasses(variant, size),
        block ? "w-full" : "w-fit",
        className,
      ]
        .filter(Boolean)
        .join(" ")}
      {...props}
    />
  );
}

/**
 * A button label that swaps to a busy text without changing the button's width: the longer of
 * the two texts is reserved invisibly (via CSS, so it is not read out or duplicated in the DOM).
 */
export function PendingLabel({
  pending,
  idle,
  busy,
}: {
  pending: boolean;
  idle: string;
  busy: string;
}) {
  const longer = busy.length > idle.length ? busy : idle;
  return (
    <span
      data-reserve={longer}
      className="flex flex-col items-center after:invisible after:block after:h-0 after:overflow-hidden after:content-[attr(data-reserve)]"
    >
      {pending ? busy : idle}
    </span>
  );
}
