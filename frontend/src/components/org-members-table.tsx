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
import { TableScroll } from "./ui/table-scroll";

type Props = {
  orgId: string;
  members: Member[];
  callerRole: OrgRole;
  callerUserId: string;
};

export type RowRights = {
  isSelf: boolean;
  canChangeRole: boolean;
  canRemove: boolean;
};

/**
 * What the caller may do on one row. Your own row only offers "Leave": changing your own role
 * could lock you out. The backend enforces the same rules; this only decides which controls show.
 */
export function rowRights(
  callerRole: OrgRole,
  callerUserId: string,
  member: Member,
): RowRights {
  const isSelf = member.userId === callerUserId;
  return {
    isSelf,
    canChangeRole: !isSelf && canManageMember(callerRole, member.role),
    canRemove: canRemoveMember(callerRole, member.role, isSelf),
  };
}

type RowProps = {
  orgId: string;
  member: Member;
  roleOptions: OrgRole[];
  rights: RowRights;
};

function RoleControl({ orgId, member, roleOptions, rights }: RowProps) {
  if (!rights.canChangeRole) return <OrgRoleBadge role={member.role} />;
  return (
    <OrgRoleSelect
      action={changeMemberRole.bind(null, orgId, member.membershipId)}
      currentRole={member.role}
      options={roleOptions}
      label={`Role for ${member.name}`}
    />
  );
}

function RemoveControl({ orgId, member, rights }: RowProps) {
  if (!rights.canRemove) return null;
  const { isSelf } = rights;
  return (
    <OrgActionButton
      action={removeMember.bind(null, orgId, member.membershipId, isSelf)}
      label={isSelf ? "Leave" : "Remove"}
      pendingLabel={isSelf ? "Leaving…" : "Removing…"}
      ariaLabel={isSelf ? "Leave organization" : `Remove ${member.name}`}
      confirm={
        isSelf
          ? "Leave this organization?"
          : `Remove ${member.name} from the organization?`
      }
      tone="danger"
    />
  );
}

/** Below md: one stacked row per member, so Role and the row action never need sideways scrolling. */
function MemberCards({ orgId, members, callerRole, callerUserId }: Props) {
  const roleOptions = assignableRoles(callerRole);
  return (
    <ul className="flex flex-col divide-y divide-line-subtle border-y border-line md:hidden">
      {members.map((member) => {
        const rights = rowRights(callerRole, callerUserId, member);
        const row = { orgId, member, roleOptions, rights };
        return (
          <li key={member.membershipId} className="flex flex-col gap-3 py-3">
            <div className="flex min-w-0 flex-col gap-0.5">
              <p className="font-medium text-primary">
                {member.name}
                {rights.isSelf && (
                  <span className="ml-2 text-caption text-secondary">
                    (you)
                  </span>
                )}
              </p>
              <p className="break-all text-sm text-secondary">{member.email}</p>
            </div>
            <div className="flex flex-col items-start gap-3">
              <RoleControl {...row} />
              <RemoveControl {...row} />
            </div>
            <p className="text-caption text-secondary">
              Joined {formatDate(member.joinedAt)}
            </p>
          </li>
        );
      })}
    </ul>
  );
}

/** From md up: a real table, inside a named, focusable scroll region. */
function MemberTable({ orgId, members, callerRole, callerUserId }: Props) {
  const roleOptions = assignableRoles(callerRole);
  return (
    <TableScroll label="Members">
      <table className={tableClass}>
        <thead className="border-b border-line text-secondary">
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
            const rights = rowRights(callerRole, callerUserId, member);
            const row = { orgId, member, roleOptions, rights };
            return (
              <tr
                key={member.membershipId}
                className="border-b border-line-subtle align-top hover:bg-hover"
              >
                <td className="py-2.5 pr-4 font-medium">
                  {member.name}
                  {rights.isSelf && (
                    <span className="ml-2 text-caption text-secondary">
                      (you)
                    </span>
                  )}
                </td>
                <td className="py-2.5 pr-4 text-secondary">{member.email}</td>
                <td className="py-2.5 pr-4">
                  <RoleControl {...row} />
                </td>
                <td className="py-2.5 pr-4 text-secondary">
                  {formatDate(member.joinedAt)}
                </td>
                <td className="py-2.5">
                  <RemoveControl {...row} />
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </TableScroll>
  );
}

// Renders the role rules as controls. Both layouts render the same rows; CSS shows one of them.
export default function OrgMembersTable(props: Props) {
  return (
    <>
      <MemberCards {...props} />
      <div className="hidden md:block">
        <MemberTable {...props} />
      </div>
    </>
  );
}
