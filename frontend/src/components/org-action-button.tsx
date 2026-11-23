"use client";

import { useActionState, useId, useRef } from "react";
import type { OrgActionState } from "@/lib/org-actions";
import { Alert } from "./ui/alert";
import { Button, PendingLabel } from "./ui/button";

type Props = {
  action: (
    previous: OrgActionState,
    formData: FormData,
  ) => Promise<OrgActionState>;
  label: string;
  pendingLabel: string;
  ariaLabel?: string;
  /** Asks for confirmation in a dialog first; cancelling sends nothing. */
  confirm?: string;
  tone?: "neutral" | "danger";
  /** "sm" inside a table row. */
  size?: "md" | "sm";
  /** Fill the width of the cell, so buttons in one column share a width. */
  block?: boolean;
};

export default function OrgActionButton({
  action,
  label,
  pendingLabel,
  ariaLabel,
  confirm,
  tone = "neutral",
  size = "md",
  block = false,
}: Props) {
  const [state, formAction, pending] = useActionState(action, {});
  const dialogRef = useRef<HTMLDialogElement>(null);
  const id = useId();
  const danger = tone === "danger";

  return (
    <form
      action={formAction}
      className={`flex flex-col items-start gap-1 ${block ? "w-full" : ""}`}
    >
      <Button
        type={confirm ? "button" : "submit"}
        variant={danger ? "danger" : "secondary"}
        size={size}
        block={block}
        disabled={pending}
        aria-label={ariaLabel}
        onClick={confirm ? () => dialogRef.current?.showModal() : undefined}
      >
        <PendingLabel pending={pending} busy={pendingLabel} idle={label} />
      </Button>

      {confirm && (
        <dialog
          ref={dialogRef}
          aria-labelledby={`${id}-title`}
          aria-describedby={`${id}-body`}
          onClick={(event) => {
            if (event.target === event.currentTarget)
              dialogRef.current?.close();
          }}
          className="enter m-auto w-[calc(100%-2rem)] max-w-sm rounded-panel border border-line bg-surface p-5 text-primary shadow-lg backdrop:bg-black/40"
        >
          <h2 id={`${id}-title`} className="text-lg font-medium">
            {label}
          </h2>
          <p id={`${id}-body`} className="mt-2 text-secondary">
            {confirm}
          </p>
          <div className="mt-5 flex justify-end gap-2">
            <Button
              type="button"
              autoFocus
              onClick={() => dialogRef.current?.close()}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              variant={danger ? "destructive" : "primary"}
              onClick={() => dialogRef.current?.close()}
            >
              {label}
            </Button>
          </div>
        </dialog>
      )}

      {state.message && <Alert tone="danger">{state.message}</Alert>}
    </form>
  );
}
