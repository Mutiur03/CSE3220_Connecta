"use client";

import { useActionState } from "react";
import type { OrgActionState } from "@/lib/org-actions";
import { Alert } from "./ui/alert";
import { Button } from "./ui/button";

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
      <Button
        type="submit"
        variant={tone === "danger" ? "danger" : "secondary"}
        disabled={pending}
        aria-label={ariaLabel}
        onClick={(event) => {
          if (confirm && !window.confirm(confirm)) event.preventDefault();
        }}
      >
        {pending ? pendingLabel : label}
      </Button>
      {state.message && <Alert tone="danger">{state.message}</Alert>}
    </form>
  );
}
