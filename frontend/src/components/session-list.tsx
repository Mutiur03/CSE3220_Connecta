import { formatRelative } from "@/lib/format";
import { revokeOtherSessions, revokeSession } from "@/lib/session-actions";
import type { DeviceSession } from "@/lib/session-api";
import { deviceLabel } from "@/lib/session-view";
import DeviceIcon from "./device-icon";
import OrgActionButton from "./org-action-button";
import { Badge } from "./ui/badge";

/** Two unknown devices must not both read "Sign out Unknown device": add what tells them apart. */
function signOutLabel(session: DeviceSession, now: Date): string {
  const details = [
    session.location,
    session.ip,
    `last active ${formatRelative(session.lastUsedAt, now)}`,
  ].filter(Boolean);
  return `Sign out ${deviceLabel(session.userAgent)}, ${details.join(", ")}`;
}

/** Rows, not cards: one device per row, the current one marked and without a sign-out button. */
export default function SessionList({
  sessions,
  now,
}: {
  sessions: DeviceSession[];
  /** The server's clock for "5 minutes ago"; passed in so rendering stays pure. */
  now: Date;
}) {
  const others = sessions.filter((session) => !session.current);
  // This device first; the rest keep the server's order (most recently active first).
  const ordered = [...sessions.filter((session) => session.current), ...others];

  return (
    <>
      {others.length > 0 && (
        <div>
          <OrgActionButton
            action={revokeOtherSessions}
            label="Sign out other devices"
            pendingLabel="Signing out�"
            confirm="Every device except this one will have to sign in again."
            tone="danger"
          />
        </div>
      )}

      {sessions.length === 0 ? (
        <p className="text-secondary">
          No active sessions. Sign in again to see this device here.
        </p>
      ) : (
        <ul className="flex flex-col divide-y divide-line-subtle border-y border-line">
          {ordered.map((session) => (
            <li
              key={session.id}
              className="flex flex-wrap items-center gap-3 py-3"
            >
              <DeviceIcon userAgent={session.userAgent} />
              <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                <p className="flex items-center gap-2 font-medium text-primary">
                  {deviceLabel(session.userAgent)}
                  {session.current && (
                    <Badge tone="accent" dot>
                      This device
                    </Badge>
                  )}
                </p>
                <p className="flex flex-wrap gap-x-4 text-sm text-secondary">
                  {session.location && <span>{session.location}</span>}
                  <span className="figure break-all">
                    {session.ip ?? "Unknown address"}
                  </span>
                  <span>
                    {session.current ? (
                      "Active now"
                    ) : (
                      <>
                        Last active{" "}
                        <time dateTime={session.lastUsedAt}>
                          {formatRelative(session.lastUsedAt, now)}
                        </time>
                      </>
                    )}
                  </span>
                </p>
              </div>
              {!session.current && (
                <OrgActionButton
                  action={revokeSession.bind(null, session.id)}
                  label="Sign out"
                  pendingLabel="Signing out…"
                  ariaLabel={signOutLabel(session, now)}
                  confirm="This device will have to sign in again."
                  tone="danger"
                  size="sm"
                />
              )}
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
