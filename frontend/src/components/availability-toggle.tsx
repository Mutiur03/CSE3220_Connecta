"use client";

import { useActionState } from "react";
import type { AvailabilityStatus } from "@/lib/assignment-api";
import { AVAILABILITY_LABELS } from "@/lib/assignment-view";
import type { OrgActionState } from "@/lib/org-actions";
import { Alert } from "./ui/alert";
import { Button, PendingLabel } from "./ui/button";

type Props = {
  /** Sets the status this button switches to; the page binds it. */
  action: (
    previous: OrgActionState,
    formData: FormData,
  ) => Promise<OrgActionState>;
  status: AvailabilityStatus;
  next: AvailabilityStatus;
};

/** The signed-in member's own status: it shows the current one and switches it. Available gets the brass status dot. */
export default function AvailabilityToggle({ action, status, next }: Props) {
  const [state, formAction, pending] = useActionState(action, {});
  const available = status === "AVAILABLE";

  return (
    <form action={formAction} className="flex flex-col items-start gap-1">
      <div className="flex items-center gap-2">
        <span className="flex items-center gap-1.5 text-sm text-secondary">
          <span
            aria-hidden="true"
            className={`size-1.5 rounded-full ${available ? "bg-highlight" : "bg-line-strong"}`}
          />
          You are {AVAILABILITY_LABELS[status].toLowerCase()}
        </span>
        <Button type="submit" size="sm" disabled={pending}>
          <PendingLabel
            pending={pending}
            busy="Saving…"
            idle={`Go ${AVAILABILITY_LABELS[next].toLowerCase()}`}
          />
        </Button>
      </div>
      {state.message && <Alert tone="danger">{state.message}</Alert>}
    </form>
  );
}
