"use client";

import { useActionState, useRef, useState, type KeyboardEvent } from "react";
import { flushSync } from "react-dom";
import { insertAtCaret } from "@/lib/caret";
import { MESSAGE_MAX_LENGTH } from "@/lib/inbox-api";
import type { OrgActionState } from "@/lib/org-actions";
import { alertText } from "@/lib/org-form-state";
import EmojiPicker from "./emoji-picker";
import { Alert } from "./ui/alert";
import { SendIcon } from "./icons";
import { Button, PendingLabel } from "./ui/button";

type Props = {
  action: (
    previous: OrgActionState,
    formData: FormData,
  ) => Promise<OrgActionState>;
  /** Who the reply goes to and where, e.g. "Nadia Rahman on Messenger"; shown as the placeholder. */
  recipient?: string;
};

/** Show the remaining characters only when the limit is near. */
const COUNTER_FROM = 100;
const BOX_ID = "reply-text";

// One frame holds the text and its footer. It takes the field's focus ring and invalid border, so the
// whole composer reads as a single input.
const FRAME =
  "flex flex-col rounded-panel border border-line-field bg-surface shadow-[0_1px_1px_rgb(0_0_0/0.03)] transition-[border-color,box-shadow] duration-150 ease-out hover:not-focus-within:border-line-strong focus-within:border-accent focus-within:shadow-none focus-within:ring-3 focus-within:ring-accent/20 has-[textarea[aria-invalid=true]]:border-danger has-[textarea[aria-invalid=true]]:focus-within:ring-danger/20 motion-reduce:transition-none";
const KEY =
  "inline-grid min-w-5 place-items-center rounded-[4px] border border-line bg-app px-1 font-sans text-[11px] leading-4 font-medium text-secondary";

/**
 * The reply box pinned under a thread: one frame with the text on top and a footer of key hints and Send.
 * It grows with its text (CSS field-sizing, capped), Enter sends and Shift+Enter adds a line. The text is
 * kept on failure and cleared on success.
 */
export default function ReplyComposer({ action, recipient }: Props) {
  const formRef = useRef<HTMLFormElement>(null);
  const boxRef = useRef<HTMLTextAreaElement>(null);
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
  const nearLimit = !over && remaining <= COUNTER_FROM;

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

  // Puts the emoji where the caret (or selection) was and leaves the caret right after it.
  const insertEmoji = (emoji: string) => {
    const box = boxRef.current;
    if (!box) return;
    const next = insertAtCaret(
      box.value,
      box.selectionStart,
      box.selectionEnd,
      emoji,
    );
    flushSync(() => setText(next.value));
    box.focus();
    box.setSelectionRange(next.caret, next.caret);
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
      <div className={FRAME}>
        <textarea
          ref={boxRef}
          id={BOX_ID}
          name="text"
          rows={2}
          value={text}
          onChange={(event) => setText(event.target.value)}
          onKeyDown={onKeyDown}
          placeholder={recipient ? `Reply to ${recipient}` : "Write a reply"}
          aria-invalid={error ? true : undefined}
          aria-describedby={`${BOX_ID}-hint${error ? ` ${BOX_ID}-error` : ""}`}
          className="max-h-48 min-h-14 w-full resize-none bg-transparent px-3 pt-2.5 pb-1 text-sm text-primary [field-sizing:content] placeholder:text-secondary focus:outline-hidden"
        />
        <div className="flex items-center gap-3 py-2 pr-2 pl-2">
          <div className="flex items-center gap-1">
            <EmojiPicker
              onPick={insertEmoji}
              onDismiss={() => boxRef.current?.focus()}
            />
          </div>
          <p
            id={`${BOX_ID}-hint`}
            className={`mr-auto text-caption ${nearLimit ? "figure text-secondary" : "flex flex-wrap items-center gap-1 text-secondary max-sm:invisible pointer-coarse:invisible"}`}
          >
            {nearLimit ? (
              `${remaining} characters left`
            ) : (
              <>
                <kbd className={KEY}>Enter</kbd> to send,
                <kbd className={KEY}>Shift</kbd>+
                <kbd className={KEY}>Enter</kbd> for a new line
              </>
            )}
          </p>
          <Button
            type="submit"
            variant="primary"
            size="sm"
            disabled={!canSend}
            className="[&_svg]:size-3.5"
          >
            <SendIcon />
            <PendingLabel pending={pending} busy="Sending…" idle="Send" />
          </Button>
        </div>
      </div>
      {error && (
        <p id={`${BOX_ID}-error`} className="text-sm text-danger">
          {error}
        </p>
      )}
      {alert && <Alert tone="danger">{alert}</Alert>}
    </form>
  );
}
