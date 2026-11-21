"use client";

import { useActionState } from "react";
import { acceptInvitation, type OrgActionState } from "@/lib/org-actions";
import { alertText } from "@/lib/org-form-state";
import { Alert } from "./ui/alert";
import { Button, PendingLabel } from "./ui/button";

/** A signed-in person whose email matches the invitation joins with one click. */
export default function InvitationJoinForm({
  token,
  organization,
}: {
  token: string;
  organization: string;
}) {
  const [state, action, pending] = useActionState<OrgActionState, FormData>(
    acceptInvitation,
    {},
  );
  const alert = alertText(state);

  return (
    <form action={action} className="flex w-full flex-col gap-4">
      <input type="hidden" name="token" value={token} />
      {alert && <Alert tone="danger">{alert}</Alert>}
      <Button type="submit" variant="primary" block disabled={pending}>
        <PendingLabel
          pending={pending}
          busy={"Joining…"}
          idle={`Join ${organization}`}
        />
      </Button>
    </form>
  );
}
