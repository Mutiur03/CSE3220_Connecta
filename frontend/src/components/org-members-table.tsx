import { changeMemberRole, removeMember } from "@/lib/org-actions";
import type { Member } from "@/lib/org-api";
import { formatDate } from "@/lib/format";
import {
  assignableRoles,
  canManageMember,
  canRemoveMember,
  type OrgRole,
} from "@/lib/org-permissions";
import OrgActionButton from "./org-action-button";
import OrgRoleBadge from "./org-role-badge";
import OrgRoleSelect from "./org-role-select";
import { tableClass } from "./org-styles";

type Props = {
  orgId: string;
  members: Member[];
  callerRole: OrgRole;
  callerUserId: string;
};

// Renders the role rules as controls. Your own row only offers "Leave": changing your own role
// could lock you out, so it is not offered.
export default function OrgMembersTable({
  orgId,
  members,
  callerRole,
  callerUserId,
}: Props) {
  const roleOptions = assignableRoles(callerRole);

  return (
    <div className="overflow-x-auto">
      <table className={tableClass}>
        <thead className="border-b border-zinc-200 text-zinc-500 dark:border-zinc-800">
          <tr>
            <th scope="col" className="py-2 pr-4 font-medium">
              Name
            </th>
            <th scope="col" className="py-2 pr-4 font-medium">
              Email
            </th>
            <th scope="col" className="py-2 pr-4 font-medium">
              Role
            </th>
            <th scope="col" className="py-2 pr-4 font-medium">
              Joined
            </th>
            <th scope="col" className="py-2 font-medium">
              <span className="sr-only">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {members.map((member) => {
            const isSelf = member.userId === callerUserId;
            const canChangeRole =
              !isSelf && canManageMember(callerRole, member.role);
            const canRemove = canRemoveMember(callerRole, member.role, isSelf);
            return (
              <tr
                key={member.membershipId}
                className="border-b border-zinc-100 align-top dark:border-zinc-900"
              >
                <td className="py-3 pr-4 font-medium">
                  {member.name}
                  {isSelf && (
                    <span className="ml-2 text-xs text-zinc-500">(you)</span>
                  )}
                </td>
                <td className="py-3 pr-4 text-zinc-600 dark:text-zinc-400">
                  {member.email}
                </td>
                <td className="py-3 pr-4">
                  {canChangeRole ? (
                    <OrgRoleSelect
                      key={`${member.membershipId}-${member.role}`}
                      action={changeMemberRole.bind(
                        null,
                        orgId,
                        member.membershipId,
                      )}
                      currentRole={member.role}
                      options={roleOptions}
                      label={`Role for ${member.name}`}
                    />
                  ) : (
                    <OrgRoleBadge role={member.role} />
                  )}
                </td>
                <td className="py-3 pr-4 text-zinc-500">
                  {formatDate(member.joinedAt)}
                </td>
                <td className="py-3">
                  {canRemove && (
                    <OrgActionButton
                      action={removeMember.bind(
                        null,
                        orgId,
                        member.membershipId,
                        isSelf,
                      )}
                      label={isSelf ? "Leave" : "Remove"}
                      pendingLabel={isSelf ? "Leaving…" : "Removing…"}
                      ariaLabel={
                        isSelf ? "Leave organization" : `Remove ${member.name}`
                      }
                      confirm={
                        isSelf
                          ? "Leave this organization?"
                          : `Remove ${member.name} from the organization?`
                      }
                      tone="danger"
                    />
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
