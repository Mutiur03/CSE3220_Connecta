import type {
  InputHTMLAttributes,
  ReactNode,
  TextareaHTMLAttributes,
} from "react";
import { fieldClasses } from "./classes";

type FieldProps = InputHTMLAttributes<HTMLInputElement> & {
  id: string;
  label: string;
  hint?: string;
  error?: string;
  /** A control that sits inside the right edge of the input (show or hide a password). */
  trailing?: ReactNode;
};

export function Field({
  id,
  label,
  hint,
  error,
  trailing,
  className,
  ...props
}: FieldProps) {
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [hintId, errorId].filter(Boolean).join(" ") || undefined;

  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-sm font-medium text-primary">
        {label}
      </label>
      <div className="relative">
        <input
          {...props}
          id={id}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={[fieldClasses, trailing && "pr-16", className]
            .filter(Boolean)
            .join(" ")}
        />
        {trailing}
      </div>
      {hint && (
        <p id={hintId} className="text-caption text-secondary">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  );
}

type TextareaFieldProps = TextareaHTMLAttributes<HTMLTextAreaElement> & {
  id: string;
  label: string;
  /** Shown after the label in the secondary colour, such as "(optional)". */
  labelNote?: string;
  hint?: string;
  error?: string;
};

/** A multi-line Field: same label, hint and error wiring, resizable vertically. */
export function TextareaField({
  id,
  label,
  labelNote,
  hint,
  error,
  className,
  ...props
}: TextareaFieldProps) {
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [hintId, errorId].filter(Boolean).join(" ") || undefined;

  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-sm font-medium text-primary">
        {label}
        {labelNote && (
          <span className="font-normal text-secondary"> {labelNote}</span>
        )}
      </label>
      <textarea
        {...props}
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={[fieldClasses, "resize-y", className]
          .filter(Boolean)
          .join(" ")}
      />
      {hint && (
        <p id={hintId} className="text-caption text-secondary">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
