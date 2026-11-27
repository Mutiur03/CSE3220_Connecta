import { createInvitation, revokeInvitation } from "@/lib/org-actions";
import type { Invitation } from "@/lib/org-api";
import { invitationStatusLabel } from "@/lib/format";
import { LocalTime } from "./local-time";
import { invitationBadgeTone } from "@/lib/invitation-view";
import { ROLE_LABELS, type OrgRole } from "@/lib/org-permissions";
import { MailIcon } from "./icons";
import { Badge } from "./ui/badge";
import OrgActionButton from "./org-action-button";
import OrgInviteForm from "./org-invite-form";

type Props = {
  orgId: string;
  invitations: Invitation[];
  roleOptions: OrgRole[];
};

function InvitationRow({
  orgId,
  invitation,
  status,
}: {
  orgId: string;
  invitation: Invitation;
  status: string;
}) {
  const pending = status === "Pending";
  return (
    <li className="flex flex-wrap items-center gap-x-4 gap-y-3 px-4 py-3">
      <div className="flex min-w-0 flex-1 basis-60 items-center gap-3">
        <span
          aria-hidden="true"
          className="grid size-9 shrink-0 place-items-center rounded-full border border-line text-secondary [&_svg]:size-4"
        >
          <MailIcon />
        </span>
        <span className="flex min-w-0 flex-col">
          <span className="truncate text-primary">{invitation.email}</span>
          <span className="text-caption text-secondary">
            Invited as {ROLE_LABELS[invitation.role].toLowerCase()}
          </span>
        </span>
      </div>
      {/* Fixed columns from sm up; an empty cell keeps its slot so rows stay aligned. */}
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2 pl-12 sm:ml-auto sm:grid sm:grid-cols-[9.5rem_8rem_6.5rem] sm:gap-x-3 sm:pl-0">
        <span className="figure text-caption text-secondary sm:justify-self-end">
          {/* Only an open invitation has an expiry worth reading. */}
          {pending && (
            <>
              Expires <LocalTime iso={invitation.expiresAt} kind="date" />
            </>
          )}
        </span>
        <span className="flex justify-center">
          <Badge tone={invitationBadgeTone(status)} dot={pending}>
            {status}
          </Badge>
        </span>
        <div className="w-26 sm:w-auto">
          {invitation.status === "PENDING" && (
            <OrgActionButton
              action={revokeInvitation.bind(null, orgId, invitation.id)}
              label="Revoke"
              pendingLabel="Revoking…"
              ariaLabel={`Revoke invitation for ${invitation.email}`}
              confirm={`Revoke the invitation for ${invitation.email}? Its link stops working.`}
              tone="danger"
              size="sm"
              block
            />
          )}
        </div>
      </div>
    </li>
  );
}

export default function OrgInvitations({
  orgId,
  invitations,
  roleOptions,
}: Props) {
  const now = new Date();

  return (
    <div className="flex flex-col gap-4">
      <OrgInviteForm
        action={createInvitation.bind(null, orgId)}
        roleOptions={roleOptions}
      />

      {invitations.length === 0 ? (
        <p className="rounded-control border border-line px-4 py-4 text-secondary">
          No invitations yet. Enter a teammate&apos;s email above to send the
          first one.
        </p>
      ) : (
        <ul
          aria-label="Invitations"
          className="flex flex-col divide-y divide-line-subtle rounded-control border border-line bg-surface"
        >
          {invitations.map((invitation) => (
            <InvitationRow
              key={invitation.id}
              orgId={orgId}
              invitation={invitation}
              status={invitationStatusLabel(
                invitation.status,
                invitation.expiresAt,
                now,
              )}
            />
          ))}
        </ul>
      )}
    </div>
  );
}
