import { createInvitation, revokeInvitation } from "@/lib/org-actions";
import type { Invitation } from "@/lib/org-api";
import { formatDate, invitationStatusLabel } from "@/lib/format";
import type { OrgRole } from "@/lib/org-permissions";
import OrgActionButton from "./org-action-button";
import OrgInviteForm from "./org-invite-form";
import { tableClass } from "./org-styles";

type Props = {
  orgId: string;
  invitations: Invitation[];
  roleOptions: OrgRole[];
};

export default function OrgInvitations({
  orgId,
  invitations,
  roleOptions,
}: Props) {
  const now = new Date();

  return (
    <div className="flex flex-col gap-6">
      <OrgInviteForm
        action={createInvitation.bind(null, orgId)}
        roleOptions={roleOptions}
      />

      {invitations.length === 0 ? (
        <p className="text-sm text-zinc-500">No invitations yet.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className={tableClass}>
            <thead className="border-b border-zinc-200 text-zinc-500 dark:border-zinc-800">
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
              {invitations.map((invitation) => (
                <tr
                  key={invitation.id}
                  className="border-b border-zinc-100 align-top dark:border-zinc-900"
                >
                  <td className="py-3 pr-4">{invitation.email}</td>
                  <td className="py-3 pr-4">{invitation.role}</td>
                  <td className="py-3 pr-4">
                    {invitationStatusLabel(
                      invitation.status,
                      invitation.expiresAt,
                      now,
                    )}
                  </td>
                  <td className="py-3 pr-4 text-zinc-500">
                    {formatDate(invitation.expiresAt)}
                  </td>
                  <td className="py-3">
                    {invitation.status === "PENDING" && (
                      <OrgActionButton
                        action={revokeInvitation.bind(
                          null,
                          orgId,
                          invitation.id,
                        )}
                        label="Revoke"
                        pendingLabel="Revoking…"
                        ariaLabel={`Revoke invitation for ${invitation.email}`}
                        tone="danger"
                      />
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
