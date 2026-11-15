import { createInvitation, revokeInvitation } from "@/lib/org-actions";
import type { Invitation } from "@/lib/org-api";
import { formatDate, invitationStatusLabel } from "@/lib/format";
import { invitationBadgeTone } from "@/lib/invitation-view";
import { ROLE_LABELS, type OrgRole } from "@/lib/org-permissions";
import { Badge } from "./ui/badge";
import { TableScroll } from "./ui/table-scroll";
import OrgActionButton from "./org-action-button";
import OrgInviteForm from "./org-invite-form";
import { tableClass } from "./org-styles";

type Props = {
  orgId: string;
  invitations: Invitation[];
  roleOptions: OrgRole[];
};

function RevokeButton({
  orgId,
  invitation,
}: {
  orgId: string;
  invitation: Invitation;
}) {
  return (
    <OrgActionButton
      action={revokeInvitation.bind(null, orgId, invitation.id)}
      label="Revoke"
      pendingLabel="Revoking…"
      ariaLabel={`Revoke invitation for ${invitation.email}`}
      confirm={`Revoke the invitation for ${invitation.email}? Its token will stop working.`}
      tone="danger"
    />
  );
}

export default function OrgInvitations({
  orgId,
  invitations,
  roleOptions,
}: Props) {
  const now = new Date();
  const rows = invitations.map((invitation) => ({
    invitation,
    status: invitationStatusLabel(invitation.status, invitation.expiresAt, now),
  }));

  return (
    <div className="flex flex-col gap-6">
      <OrgInviteForm
        action={createInvitation.bind(null, orgId)}
        roleOptions={roleOptions}
      />

      {rows.length === 0 ? (
        <p className="text-sm text-ink-muted">No invitations yet.</p>
      ) : (
        <>
          <ul className="flex flex-col gap-3 sm:hidden">
            {rows.map(({ invitation, status }) => (
              <li
                key={invitation.id}
                className="flex flex-col gap-3 rounded-card border border-line bg-surface p-4"
              >
                <div className="flex items-start justify-between gap-3">
                  <p className="min-w-0 break-all font-medium">
                    {invitation.email}
                  </p>
                  <Badge tone={invitationBadgeTone(status)}>{status}</Badge>
                </div>
                <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
                  <dt className="text-ink-muted">Role</dt>
                  <dd>{ROLE_LABELS[invitation.role]}</dd>
                  <dt className="text-ink-muted">Expires</dt>
                  <dd>{formatDate(invitation.expiresAt)}</dd>
                </dl>
                {invitation.status === "PENDING" && (
                  <RevokeButton orgId={orgId} invitation={invitation} />
                )}
              </li>
            ))}
          </ul>

          <TableScroll label="Invitations" className="hidden sm:block">
            <table className={tableClass}>
              <thead className="border-b border-line text-ink-muted">
                <tr>
                  <th scope="col" className="py-2 pr-4 font-medium">
                    Email
                  </th>
                  <th scope="col" className="py-2 pr-4 font-medium">
                    Role
                  </th>
                  <th scope="col" className="py-2 pr-4 font-medium">
                    Status
                  </th>
                  <th scope="col" className="py-2 pr-4 font-medium">
                    Expires
                  </th>
                  <th scope="col" className="py-2 font-medium">
                    <span className="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {rows.map(({ invitation, status }) => (
                  <tr
                    key={invitation.id}
                    className="border-b border-line align-top"
                  >
                    <td className="py-3 pr-4">{invitation.email}</td>
                    <td className="py-3 pr-4">
                      {ROLE_LABELS[invitation.role]}
                    </td>
                    <td className="py-3 pr-4">
                      <Badge tone={invitationBadgeTone(status)}>{status}</Badge>
                    </td>
                    <td className="py-3 pr-4 text-ink-muted">
                      {formatDate(invitation.expiresAt)}
                    </td>
                    <td className="py-3">
                      {invitation.status === "PENDING" && (
                        <RevokeButton orgId={orgId} invitation={invitation} />
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </TableScroll>
        </>
      )}
    </div>
  );
}
