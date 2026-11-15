"use client";

import { useActionState } from "react";
import type { OrgActionState } from "@/lib/org-actions";
import { buttonClass } from "./org-styles";

type Props = {
  action: (
    previous: OrgActionState,
    formData: FormData,
  ) => Promise<OrgActionState>;
  label: string;
  pendingLabel: string;
  ariaLabel?: string;
  /** Asks for confirmation first; cancelling stops the click before anything is sent. */
  confirm?: string;
  tone?: "neutral" | "danger";
};

export default function OrgActionButton({
  action,
  label,
  pendingLabel,
  ariaLabel,
  confirm,
  tone = "neutral",
}: Props) {
  const [state, formAction, pending] = useActionState(action, {});

  return (
    <form action={formAction} className="flex flex-col items-start gap-1">
      <button
        type="submit"
        disabled={pending}
        aria-label={ariaLabel}
        onClick={(event) => {
          if (confirm && !window.confirm(confirm)) event.preventDefault();
        }}
        className={buttonClass(tone)}
      >
        {pending ? pendingLabel : label}
      </button>
      {state.message && (
        <p role="alert" className="text-sm text-red-600">
          {state.message}
        </p>
      )}
    </form>
  );
}
