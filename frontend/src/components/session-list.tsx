import { formatDateTime } from "@/lib/format";
import { revokeOtherSessions, revokeSession } from "@/lib/session-actions";
import type { DeviceSession } from "@/lib/session-api";
import { deviceLabel } from "@/lib/session-view";
import OrgActionButton from "./org-action-button";
import { Badge } from "./ui/badge";

/** Rows, not cards: one device per row, the current one marked and without a sign-out button. */
export default function SessionList({
  sessions,
}: {
  sessions: DeviceSession[];
}) {
  const others = sessions.filter((session) => !session.current);

  return (
    <section aria-labelledby="sessions-heading" className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="sessions-heading" className="text-lg font-medium">
          Signed-in devices
        </h2>
        {others.length > 0 && (
          <OrgActionButton
            action={revokeOtherSessions}
            label="Sign out other devices"
            pendingLabel="Signing out…"
            confirm="Every device except this one will have to sign in again."
            tone="danger"
          />
        )}
      </div>

      {sessions.length === 0 ? (
        <p className="text-secondary">
          No active sessions. Sign in again to see this device here.
        </p>
      ) : (
        <ul className="flex flex-col divide-y divide-line-subtle border-y border-line">
          {sessions.map((session) => (
            <li
              key={session.id}
              className="flex flex-wrap items-center justify-between gap-3 py-3"
            >
              <div className="flex min-w-0 flex-col gap-0.5">
                <p className="flex items-center gap-2 font-medium text-primary">
                  {deviceLabel(session.userAgent)}
                  {session.current && <Badge tone="accent">This device</Badge>}
                </p>
                <p className="flex flex-wrap gap-x-4 text-sm text-secondary">
                  <span className="figure break-all">
                    {session.ip ?? "Unknown address"}
                  </span>
                  <span>
                    Last active{" "}
                    <time dateTime={session.lastUsedAt} className="figure">
                      {formatDateTime(session.lastUsedAt)}
                    </time>
                  </span>
                </p>
              </div>
              {!session.current && (
                <OrgActionButton
                  action={revokeSession.bind(null, session.id)}
                  label="Sign out"
                  pendingLabel="Signing out…"
                  ariaLabel={`Sign out ${deviceLabel(session.userAgent)}`}
                  confirm="This device will have to sign in again."
                  tone="danger"
                  size="sm"
                />
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
