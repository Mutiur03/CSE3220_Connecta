"use client";

import { useActionState } from "react";
import type { OrgActionState } from "@/lib/org-actions";
import { Alert } from "./ui/alert";
import { Button, PendingLabel } from "./ui/button";

type Props = {
  /** Starts the login. It redirects the browser to Meta, so it only returns when something failed. */
  action: () => Promise<OrgActionState>;
  idleLabel: string;
  busyLabel: string;
  variant: "primary" | "secondary";
};

/** A button that starts a Meta login. Its width does not change while it waits. */
export default function MetaConnectButton({
  action,
  idleLabel,
  busyLabel,
  variant,
}: Props) {
  const [state, formAction, pending] = useActionState(async () => action(), {});

  return (
    <form action={formAction} className="flex flex-col gap-2">
      <Button
        type="submit"
        variant={variant}
        disabled={pending}
        aria-busy={pending}
      >
        <PendingLabel pending={pending} idle={idleLabel} busy={busyLabel} />
      </Button>
      {state.message && <Alert tone="danger">{state.message}</Alert>}
    </form>
  );
}
