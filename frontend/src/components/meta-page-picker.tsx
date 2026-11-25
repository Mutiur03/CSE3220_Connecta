"use client";

import { useActionState } from "react";
import type { MetaAvailablePage } from "@/lib/meta-api";
import type { OrgActionState } from "@/lib/org-actions";
import { Alert } from "./ui/alert";
import { Badge } from "./ui/badge";
import { Button, PendingLabel } from "./ui/button";

type Props = {
  /** The Pages the grant includes. The server action reads the ticked ids from the form. */
  pages: MetaAvailablePage[];
  /** A formatted "connected on" date per Page id, for the Pages already connected. */
  connectedOn?: Record<string, string>;
  /** A warning per Page id, shown under the Page (a failed import of past conversations). */
  notices?: Record<string, string>;
  /** The "last webhook" line per connected Page id. */
  webhookLines?: Record<string, string>;
  action: (
    previous: OrgActionState,
    formData: FormData,
  ) => Promise<OrgActionState>;
};

/**
 * Pick the Facebook Pages to connect. A connected Page is ticked by default; submitting again re-subscribes it, so
 * a Page connected before a new webhook field was added starts receiving that field.
 */
export default function MetaPagePicker({
  pages,
  connectedOn = {},
  notices = {},
  webhookLines = {},
  action,
}: Props) {
  const [state, formAction, pending] = useActionState(action, {});

  return (
    <form action={formAction} className="flex flex-col gap-4">
      <fieldset className="flex flex-col divide-y divide-line-subtle rounded-control border border-line">
        <legend className="sr-only">Facebook Pages</legend>
        {pages.map((page) => (
          <label
            key={page.pageId}
            className="flex min-h-14 cursor-pointer items-center justify-between gap-4 px-4 py-2 transition-colors duration-150 ease-out first:rounded-t-control last:rounded-b-control hover:bg-hover motion-reduce:transition-none"
          >
            <span className="flex min-w-0 items-center gap-3">
              <input
                type="checkbox"
                name="pageId"
                value={page.pageId}
                disabled={pending}
                defaultChecked={page.connected}
                className="size-4 shrink-0 accent-accent"
              />
              <span className="flex min-w-0 flex-col">
                <span className="truncate text-primary">{page.name}</span>
                <span className="figure text-caption text-secondary">
                  Page {page.pageId}
                </span>
                {page.connected && webhookLines[page.pageId] && (
                  <span className="text-caption text-secondary">
                    {webhookLines[page.pageId]}
                  </span>
                )}
                {notices[page.pageId] && (
                  <span className="text-caption text-danger">
                    {notices[page.pageId]}
                  </span>
                )}
              </span>
            </span>
            {page.connected ? (
              <span className="flex shrink-0 flex-col items-end gap-0.5">
                <Badge tone="success">Connected</Badge>
                {connectedOn[page.pageId] && (
                  <span className="figure text-caption text-secondary">
                    {connectedOn[page.pageId]}
                  </span>
                )}
              </span>
            ) : (
              <span className="shrink-0 text-caption text-secondary">
                Not connected
              </span>
            )}
          </label>
        ))}
      </fieldset>
      {state.message && <Alert tone="danger">{state.message}</Alert>}
      {state.notice && <Alert tone="success">{state.notice}</Alert>}
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
        <Button
          type="submit"
          variant="primary"
          disabled={pending || pages.length === 0}
          aria-busy={pending}
        >
          <PendingLabel
            pending={pending}
            idle="Connect selected Pages"
            busy="Connecting…"
          />
        </Button>
        <p className="text-caption text-secondary">
          Connecting a Page again refreshes its message subscription.
        </p>
      </div>
    </form>
  );
}
