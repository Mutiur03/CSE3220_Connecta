"use client";

import { useState } from "react";
import { copyText } from "@/lib/copy-text";
import { Button } from "./ui/button";
import { Card } from "./ui/card";
import { Field } from "./ui/field";

type CopyStatus = "idle" | "copied" | "failed";

const STATUS_TEXT: Record<CopyStatus, string> = {
  idle: "",
  copied: "Token copied.",
  failed: "Copy failed. Select the token and copy it by hand.",
};

/** Shows a new invitation token once, with a copy button. Remount it per token (key). */
export default function OrgTokenBox({ token }: { token: string }) {
  const [copyStatus, setCopyStatus] = useState<CopyStatus>("idle");

  async function copy() {
    setCopyStatus((await copyText(token)) ? "copied" : "failed");
  }

  return (
    <Card className="flex max-w-xl flex-col gap-3 p-4">
      <p className="text-sm font-medium">
        Copy this token now. It is shown only once.
      </p>
      <div className="flex flex-col gap-2 sm:flex-row sm:items-end">
        <div className="min-w-0 flex-1">
          <Field
            id="invitation-token"
            label="Invitation token"
            readOnly
            value={token}
            onFocus={(event) => event.currentTarget.select()}
            className="font-mono text-xs"
          />
        </div>
        <Button type="button" onClick={copy}>
          Copy token
        </Button>
      </div>
      <p
        role="status"
        className={`min-h-5 text-caption ${copyStatus === "failed" ? "text-danger" : "text-success"}`}
      >
        {STATUS_TEXT[copyStatus]}
      </p>
      <p className="text-caption text-ink-muted">
        Nothing is emailed. Share the token with the invitee, who pastes it at
        /invitations/accept.
      </p>
    </Card>
  );
}
