"use client";

import { useState } from "react";
import { copyText } from "@/lib/copy-text";
import { Button } from "./ui/button";
import { Panel } from "./ui/panel";
import { Field } from "./ui/field";

type CopyStatus = "idle" | "copied" | "failed";

const STATUS_TEXT: Record<CopyStatus, string> = {
  idle: "",
  copied: "Link copied.",
  failed: "Copy failed. Select the link and copy it by hand.",
};

/** The page the invitee opens to join. The token is URL-safe, but encode it anyway. */
export function invitationLink(origin: string, token: string): string {
  return `${origin}/invitations/accept?token=${encodeURIComponent(token)}`;
}

/**
 * Shows a new invitation link once, with a copy button. Remount it per token (key). It only mounts
 * after the invite action returns, so it always renders in the browser and can read the origin.
 */
export default function OrgTokenBox({ token }: { token: string }) {
  const [copyStatus, setCopyStatus] = useState<CopyStatus>("idle");
  const origin = typeof window === "undefined" ? "" : window.location.origin;
  const link = invitationLink(origin, token);

  async function copy() {
    setCopyStatus((await copyText(link)) ? "copied" : "failed");
  }

  return (
    <Panel className="flex flex-col gap-3">
      <p className="font-medium">
        Copy the invitation link now. It is shown only once.
      </p>
      <div className="flex flex-col gap-2 sm:flex-row sm:items-end">
        <div className="min-w-0 flex-1">
          <Field
            id="invitation-token"
            label="Invitation link"
            readOnly
            autoComplete="off"
            value={link}
            onFocus={(event) => event.currentTarget.select()}
            className="figure"
          />
        </div>
        <Button type="button" onClick={copy}>
          Copy link
        </Button>
      </div>
      <p
        role="status"
        className={`min-h-5 text-caption ${copyStatus === "failed" ? "text-danger" : "text-success"}`}
      >
        {STATUS_TEXT[copyStatus]}
      </p>
      <p className="text-caption text-secondary">
        When outgoing email is set up, the invitee also gets this link by email.
        Otherwise send it to them yourself. A new person creates an account on
        that page; someone with an account signs in first.
      </p>
    </Panel>
  );
}
