"use client";

import { useActionState, useRef, useState, type KeyboardEvent } from "react";
import { MESSAGE_MAX_LENGTH } from "@/lib/inbox-api";
import type { OrgActionState } from "@/lib/org-actions";
import { alertText } from "@/lib/org-form-state";
import { Alert } from "./ui/alert";
import { Button, PendingLabel } from "./ui/button";
import { fieldClasses } from "./ui/classes";

type Props = {
  action: (
    previous: OrgActionState,
    formData: FormData,
  ) => Promise<OrgActionState>;
};

/** Show the remaining characters only when the limit is near. */
const COUNTER_FROM = 100;
const BOX_ID = "reply-text";

/**
 * The reply box pinned under a thread. It grows with its text (CSS field-sizing, capped), Enter sends and
 * Shift+Enter adds a line. The text is kept on failure and cleared on success.
 */
export default function ReplyComposer({ action }: Props) {
  const formRef = useRef<HTMLFormElement>(null);
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
  const canSend = !pending && !over && text.trim().length > 0;
  const alert = alertText(state);
  const error = over
    ? `Remove ${-remaining} characters to send.`
    : state.errors?.text;
  const hint =
    !over && remaining <= COUNTER_FROM
      ? `${remaining} characters left`
      : "Enter to send, Shift+Enter for a new line";

  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (
      event.key !== "Enter" ||
      event.shiftKey ||
      event.nativeEvent.isComposing
    )
      return;
    event.preventDefault();
    if (canSend) formRef.current?.requestSubmit();
  };

  return (
    <form
      ref={formRef}
      action={formAction}
      noValidate
      className="flex flex-col gap-2"
    >
      <label htmlFor={BOX_ID} className="sr-only">
        Reply
      </label>
      <div className="flex items-end gap-2">
        <textarea
          id={BOX_ID}
          name="text"
          rows={1}
          value={text}
          onChange={(event) => setText(event.target.value)}
          onKeyDown={onKeyDown}
          placeholder="Write a reply"
          aria-invalid={error ? true : undefined}
          aria-describedby={`${BOX_ID}-hint${error ? ` ${BOX_ID}-error` : ""}`}
          className={`${fieldClasses} max-h-48 resize-none [field-sizing:content]`}
        />
        <Button type="submit" variant="primary" disabled={!canSend}>
          <PendingLabel pending={pending} busy="Sending…" idle="Send" />
        </Button>
      </div>
      <p id={`${BOX_ID}-hint`} className="text-caption text-secondary">
        {hint}
      </p>
      {error && (
        <p id={`${BOX_ID}-error`} className="text-sm text-danger">
          {error}
        </p>
      )}
      {alert && <Alert tone="danger">{alert}</Alert>}
    </form>
  );
}
