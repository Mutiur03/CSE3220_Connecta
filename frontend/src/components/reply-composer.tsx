"use client";

import {
  useActionState,
  useEffect,
  useRef,
  useState,
  type ClipboardEvent,
  type DragEvent,
  type KeyboardEvent,
} from "react";
import { flushSync } from "react-dom";
import { ATTACHMENT_ACCEPT, attachmentProblem } from "@/lib/attachment-rules";
import { insertAtCaret } from "@/lib/caret";
import { MESSAGE_MAX_LENGTH } from "@/lib/inbox-api";
import type { OrgActionState } from "@/lib/org-actions";
import { alertText } from "@/lib/org-form-state";
import AttachmentChip from "./attachment-chip";
import EmojiPicker from "./emoji-picker";
import { Alert } from "./ui/alert";
import { MicrophoneIcon, PaperclipIcon, SendIcon } from "./icons";
import { Button, PendingLabel } from "./ui/button";
import { useVoiceRecorder } from "./use-voice-recorder";

type Action = (
  previous: OrgActionState,
  formData: FormData,
) => Promise<OrgActionState>;

type Props = {
  /** Sends the text (`text` field). */
  action: Action;
  /** Sends one file (`file` field) as its own message. */
  attachAction: Action;
  /** Who the reply goes to and where, e.g. "Nadia Rahman on Messenger"; shown as the placeholder. */
  recipient?: string;
};

/** Show the remaining characters only when the limit is near. */
const COUNTER_FROM = 100;
const BOX_ID = "reply-text";
const STOP_ID = "reply-recording-stop";
const SECONDS_PER_MINUTE = 60;

/** 75 -> "1:15". */
const clock = (seconds: number) =>
  `${Math.floor(seconds / SECONDS_PER_MINUTE)}:${String(seconds % SECONDS_PER_MINUTE).padStart(2, "0")}`;

const failed = (state: OrgActionState) =>
  Boolean(state.message || state.errors);

// One frame holds the file, the text and the footer. It takes the field's focus ring and invalid border,
// so the whole composer reads as a single input; a file dragged over it shows the same ring.
const FRAME =
  "flex min-w-0 flex-col rounded-panel border border-line-field bg-surface shadow-[0_1px_1px_rgb(0_0_0/0.03)] transition-[border-color,box-shadow,background-color] duration-150 ease-out hover:not-focus-within:border-line-strong focus-within:border-accent focus-within:shadow-none focus-within:ring-3 focus-within:ring-accent/20 has-[textarea[aria-invalid=true]]:border-danger has-[textarea[aria-invalid=true]]:focus-within:ring-danger/20 data-dragging:border-accent data-dragging:bg-selected data-dragging:ring-3 data-dragging:ring-accent/20 motion-reduce:transition-none";
const TOOL =
  "grid size-8 shrink-0 place-items-center rounded-control text-secondary transition-colors duration-150 ease-out hover:bg-hover hover:text-primary disabled:pointer-events-none disabled:opacity-50 motion-reduce:transition-none pointer-coarse:size-11 [&_svg]:size-4.5";
const KEY =
  "inline-grid min-w-5 place-items-center rounded-[4px] border border-line bg-app px-1 font-sans text-[11px] leading-4 font-medium text-secondary";

/**
 * The reply box pinned under a thread: one frame with an optional file chip, the text, and a footer of
 * tools, key hints and Send. It grows with its text (CSS field-sizing, capped), Enter sends and
 * Shift+Enter adds a line. A file comes from the paperclip, a drop on the frame, a pasted image or the
 * microphone, whose recording takes the text row's place until it is stopped or cancelled. Meta
 * cannot carry a file and text in one message, so the file goes first and the text second. What went
 * out is cleared; what did not is kept.
 */
export default function ReplyComposer({
  action,
  attachAction,
  recipient,
}: Props) {
  const formRef = useRef<HTMLFormElement>(null);
  const boxRef = useRef<HTMLTextAreaElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [text, setText] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string>();
  const [announcement, setAnnouncement] = useState("");
  const [dragging, setDragging] = useState(false);
  const [state, formAction, pending] = useActionState(
    async (previous: OrgActionState, formData: FormData) => {
      setFileError(undefined);
      const hasText = String(formData.get("text") ?? "").trim().length > 0;
      if (file) {
        const upload = new FormData();
        upload.append("file", file);
        const sent = await attachAction(previous, upload);
        if (failed(sent)) return sent;
        setFile(null);
        if (!hasText) return sent;
        const next = await action(previous, formData);
        if (!failed(next)) {
          setText("");
          return next;
        }
        const reason = next.message ?? next.errors?.text ?? "";
        return {
          message: `The file was sent, but the text was not. ${reason}`.trim(),
        };
      }
      const next = await action(previous, formData);
      if (!failed(next)) setText("");
      return next;
    },
    {},
  );

  // The box is disabled while sending, which drops its focus; give it back so typing can go on.
  const wasPending = useRef(false);
  useEffect(() => {
    if (wasPending.current && !pending) boxRef.current?.focus();
    wasPending.current = pending;
  }, [pending]);

  // One file at a time: a new one replaces the old. A file that breaks a rule is not kept.
  const attach = (picked: File) => {
    const problem = attachmentProblem(picked);
    setFileError(problem);
    setAnnouncement(problem ?? `Attached ${picked.name}`);
    if (!problem) setFile(picked);
    return !problem;
  };

  // A recording is attached like a picked file, so the same checks, chip and send order apply.
  const voice = useVoiceRecorder({
    onStarted: () => setAnnouncement("Recording started"),
    onRecorded: (recorded, capped) => {
      if (attach(recorded))
        setAnnouncement(
          capped
            ? "Recording reached the time limit and was attached"
            : "Recording attached",
        );
    },
    onError: (message) => {
      setFileError(message);
      setAnnouncement(message);
    },
  });
  const recording = voice.status === "recording" || voice.status === "saving";
  const micBusy = voice.status !== "idle";

  // Stop takes focus when recording begins; the box gets it back when the recording bar goes away.
  const wasRecording = useRef(false);
  useEffect(() => {
    if (recording && !wasRecording.current)
      document.getElementById(STOP_ID)?.focus();
    if (!recording && wasRecording.current) boxRef.current?.focus();
    wasRecording.current = recording;
  }, [recording]);

  const startRecording = () => {
    setFileError(undefined);
    void voice.start();
  };
  const cancelRecording = () => {
    voice.cancel();
    setAnnouncement("Recording cancelled");
  };

  const remaining = MESSAGE_MAX_LENGTH - text.length;
  const over = remaining < 0;
  const canSend =
    !pending && !micBusy && !over && (text.trim().length > 0 || file !== null);
  const alert = alertText(state);
  const error = over
    ? `Remove ${-remaining} characters to send.`
    : (state.errors?.text ?? fileError);
  const nearLimit = !over && remaining <= COUNTER_FROM;

  const removeFile = () => {
    if (file) setAnnouncement(`Removed ${file.name}`);
    setFile(null);
    setFileError(undefined);
    boxRef.current?.focus();
  };

  // Only a pasted file is taken over; pasted text goes into the box as usual.
  const onPaste = (event: ClipboardEvent<HTMLTextAreaElement>) => {
    const pasted = event.clipboardData.files[0];
    if (!pasted) return;
    event.preventDefault();
    attach(pasted);
  };

  const onDragOver = (event: DragEvent<HTMLElement>) => {
    if (
      pending ||
      micBusy ||
      !Array.from(event.dataTransfer.types).includes("Files")
    )
      return;
    event.preventDefault();
    event.dataTransfer.dropEffect = "copy";
    setDragging(true);
  };
  const onDragLeave = (event: DragEvent<HTMLElement>) => {
    if (!event.currentTarget.contains(event.relatedTarget as Node | null))
      setDragging(false);
  };
  const onDrop = (event: DragEvent<HTMLElement>) => {
    setDragging(false);
    const dropped = event.dataTransfer.files[0];
    if (pending || micBusy || !dropped) return;
    event.preventDefault();
    attach(dropped);
  };

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
      <fieldset
        disabled={pending}
        data-dragging={dragging || undefined}
        onDragEnter={onDragOver}
        onDragOver={onDragOver}
        onDragLeave={onDragLeave}
        onDrop={onDrop}
        className={FRAME}
      >
        {file && (
          <div className="flex min-w-0 px-2 pt-2">
            <AttachmentChip file={file} onRemove={removeFile} />
          </div>
        )}
        {recording && (
          <div
            onKeyDown={(event) => {
              if (event.key !== "Escape") return;
              event.preventDefault();
              cancelRecording();
            }}
            className="flex min-h-14 min-w-0 items-center gap-3 px-3 pt-2.5 pb-1"
          >
            <span
              aria-hidden="true"
              className="size-2 shrink-0 rounded-full bg-danger motion-safe:animate-pulse"
            />
            <span className="figure text-sm text-primary">
              {clock(voice.seconds)}
            </span>
            <span className="mr-auto truncate text-sm text-secondary">
              {voice.status === "saving"
                ? "Preparing the recording…"
                : "Recording"}
            </span>
            <Button
              type="button"
              size="sm"
              onClick={cancelRecording}
              aria-label="Cancel recording"
            >
              Cancel
            </Button>
            <Button
              id={STOP_ID}
              type="button"
              size="sm"
              onClick={voice.stop}
              disabled={voice.status === "saving"}
              aria-label="Stop and attach recording"
            >
              Stop
            </Button>
          </div>
        )}
        <textarea
          ref={boxRef}
          hidden={recording}
          disabled={micBusy}
          id={BOX_ID}
          name="text"
          rows={2}
          value={text}
          onChange={(event) => setText(event.target.value)}
          onKeyDown={onKeyDown}
          onPaste={onPaste}
          placeholder={recipient ? `Reply to ${recipient}` : "Write a reply"}
          aria-invalid={error ? true : undefined}
          aria-describedby={`${BOX_ID}-hint${error ? ` ${BOX_ID}-error` : ""}`}
          className="max-h-48 min-h-14 w-full resize-none bg-transparent px-3 pt-2.5 pb-1 text-sm text-primary [field-sizing:content] placeholder:text-secondary focus:outline-hidden"
        />
        <div className="flex items-center gap-3 py-2 pr-2 pl-2">
          <div className="flex items-center gap-1">
            {/* display: contents, so this fieldset only switches its tools off while the microphone is on. */}
            <fieldset disabled={micBusy} className="contents">
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                aria-label="Attach a file"
                title="Attach a file"
                className={TOOL}
              >
                <PaperclipIcon />
              </button>
              <input
                ref={fileInputRef}
                type="file"
                accept={ATTACHMENT_ACCEPT}
                tabIndex={-1}
                aria-hidden="true"
                className="sr-only"
                onChange={(event) => {
                  const picked = event.target.files?.[0];
                  // Cleared so picking the same file again still fires a change.
                  event.target.value = "";
                  if (picked) attach(picked);
                }}
              />
              <EmojiPicker
                onPick={insertEmoji}
                onDismiss={() => boxRef.current?.focus()}
              />
            </fieldset>
            {voice.supported && (
              <button
                type="button"
                onClick={startRecording}
                disabled={micBusy}
                aria-label="Record a voice message"
                title="Record a voice message"
                className={TOOL}
              >
                <MicrophoneIcon />
              </button>
            )}
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
      </fieldset>
      <p role="status" aria-live="polite" className="sr-only">
        {announcement}
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
