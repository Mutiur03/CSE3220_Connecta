"use client";

import { useActionState, useState } from "react";
import { MESSAGE_MAX_LENGTH } from "@/lib/inbox-api";
import type { OrgActionState } from "@/lib/org-actions";
import { alertText } from "@/lib/org-form-state";
import { Alert } from "./ui/alert";
import { Button, PendingLabel } from "./ui/button";
import { TextareaField } from "./ui/field";

type Props = {
  action: (
    previous: OrgActionState,
    formData: FormData,
  ) => Promise<OrgActionState>;
};

/** Show the remaining characters only when the limit is near. */
const COUNTER_FROM = 100;

/** The reply box under a thread. The text is kept on failure and cleared on success. */
export default function ReplyComposer({ action }: Props) {
  const [text, setText] = useState("");
  const [state, formAction, pending] = useActionState(
    async (previous: OrgActionState, formData: FormData) => {
      const next = await action(previous, formData);
      if (!next.message && !next.errors) setText("");
      return next;
    },
    {},
  );
  const remaining = MESSAGE_MAX_LENGTH - text.length;
  const over = remaining < 0;
  const alert = alertText(state);

  return (
    <form action={formAction} noValidate className="flex flex-col gap-3">
      <TextareaField
        id="reply-text"
        name="text"
        label="Reply"
        rows={3}
        value={text}
        onChange={(event) => setText(event.target.value)}
        error={
          over ? `Remove ${-remaining} characters to send.` : state.errors?.text
        }
        hint={
          !over && remaining <= COUNTER_FROM
            ? `${remaining} characters left`
            : undefined
        }
      />
      {alert && <Alert tone="danger">{alert}</Alert>}
      <Button
        type="submit"
        variant="primary"
        disabled={pending || over || !text.trim()}
      >
        <PendingLabel pending={pending} busy="Sending…" idle="Send" />
      </Button>
    </form>
  );
}
