"use client";

import { useActionState } from "react";
import type { MetaAvailablePage } from "@/lib/meta-api";
import type { OrgActionState } from "@/lib/org-actions";
import { Alert } from "./ui/alert";
import { Button, PendingLabel } from "./ui/button";

type Props = {
  /** The Pages the grant includes. The server action reads the ticked ids from the form. */
  pages: MetaAvailablePage[];
  action: (
    previous: OrgActionState,
    formData: FormData,
  ) => Promise<OrgActionState>;
};

/**
 * Pick the Facebook Pages to connect. A connected Page is ticked by default; submitting again re-subscribes it, so
 * a Page connected before a new webhook field was added starts receiving that field.
 */
export default function MetaPagePicker({ pages, action }: Props) {
  const [state, formAction, pending] = useActionState(action, {});

  return (
    <form action={formAction} className="flex flex-col gap-4">
      <fieldset className="flex flex-col divide-y divide-line-subtle border-y border-line-subtle">
        <legend className="sr-only">Facebook Pages</legend>
        {pages.map((page) => (
          <label
            key={page.pageId}
            className="flex items-center justify-between gap-4 py-3"
          >
            <span className="flex items-center gap-3">
              <input
                type="checkbox"
                name="pageId"
                value={page.pageId}
                disabled={pending}
                defaultChecked={page.connected}
                className="size-4 accent-accent"
              />
              <span className="text-primary">{page.name}</span>
            </span>
            <span className="text-sm text-secondary tabular-nums">
              {page.connected ? "Connected · refresh" : "Not connected"}
            </span>
          </label>
        ))}
      </fieldset>
      {state.message && <Alert tone="danger">{state.message}</Alert>}
      {state.notice && <Alert tone="success">{state.notice}</Alert>}
      <Button
        type="submit"
        variant="primary"
        disabled={pending || pages.length === 0}
        aria-busy={pending}
        className="self-start"
      >
        <PendingLabel
          pending={pending}
          idle="Connect selected Pages"
          busy="Connecting…"
        />
      </Button>
    </form>
  );
}
