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
  size = "md",
}: {
  orgId: string;
  invitation: Invitation;
  size?: "md" | "sm";
}) {
  return (
    <OrgActionButton
      action={revokeInvitation.bind(null, orgId, invitation.id)}
      label="Revoke"
      pendingLabel="Revoking…"
      ariaLabel={`Revoke invitation for ${invitation.email}`}
      confirm={`Revoke the invitation for ${invitation.email}? Its token will stop working.`}
      tone="danger"
      size={size}
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
        <p className="text-sm text-secondary">No invitations yet.</p>
      ) : (
        <>
          <ul className="flex flex-col divide-y divide-line-subtle border-y border-line sm:hidden">
            {rows.map(({ invitation, status }) => (
              <li key={invitation.id} className="flex flex-col gap-3 py-3">
                <div className="flex items-start justify-between gap-3">
                  <p className="min-w-0 break-all font-medium">
                    {invitation.email}
                  </p>
                  <Badge
                    tone={invitationBadgeTone(status)}
                    dot={status === "Pending"}
                  >
                    {status}
                  </Badge>
                </div>
                <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
                  <dt className="text-secondary">Role</dt>
                  <dd>{ROLE_LABELS[invitation.role]}</dd>
                  <dt className="text-secondary">Expires</dt>
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
              <thead className="border-b border-line text-secondary">
                <tr>
                  <th scope="col" className="px-4 py-2 font-medium">
                    Email
                  </th>
                  <th scope="col" className="px-4 py-2 font-medium">
                    Role
                  </th>
                  <th scope="col" className="px-4 py-2 font-medium">
                    Status
                  </th>
                  <th scope="col" className="px-4 py-2 font-medium">
                    Expires
                  </th>
                  <th scope="col" className="px-4 py-2 text-right font-medium">
                    <span className="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {rows.map(({ invitation, status }) => (
                  <tr
                    key={invitation.id}
                    className="h-14 border-b border-line-subtle transition-colors last:border-b-0 hover:bg-hover/60"
                  >
                    <td className="px-4 py-2 align-middle">
                      {invitation.email}
                    </td>
                    <td className="px-4 py-2 align-middle">
                      {ROLE_LABELS[invitation.role]}
                    </td>
                    <td className="px-4 py-2 align-middle">
                      <Badge
                        tone={invitationBadgeTone(status)}
                        dot={status === "Pending"}
                      >
                        {status}
                      </Badge>
                    </td>
                    <td className="px-4 py-2 align-middle text-secondary">
                      {formatDate(invitation.expiresAt)}
                    </td>
                    <td className="px-4 py-2 align-middle">
                      <div className="flex justify-end">
                        {invitation.status === "PENDING" && (
                          <RevokeButton
                            orgId={orgId}
                            invitation={invitation}
                            size="sm"
                          />
                        )}
                      </div>
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
