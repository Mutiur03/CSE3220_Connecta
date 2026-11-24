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
import { Badge } from "./ui/badge";

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

/** The caller's own row first, everyone else in the order the server sent them. */
export function selfFirst(members: Member[], callerUserId: string): Member[] {
  return [
    ...members.filter((member) => member.userId === callerUserId),
    ...members.filter((member) => member.userId !== callerUserId),
  ];
}

/** Up to two initials from the name, else the first letter of the email. */
export function memberInitials(name: string, email: string): string {
  const fromName = name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part.charAt(0))
    .join("");
  return (fromName || email.charAt(0)).toUpperCase();
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
          ? "Leave this organization? You lose access until an admin invites you again."
          : `Remove ${member.name} from the organization? They lose access right away.`
      }
      tone="danger"
      size="sm"
      block
    />
  );
}

// Date, role (a select, or a badge), action. The role is centred in its column and every action
// button fills the same column width, so Remove and Leave are the same size.
const ROW_COLUMNS =
  "flex flex-wrap items-center gap-x-4 gap-y-2 pl-12 sm:ml-auto sm:grid sm:grid-cols-[9.5rem_8rem_6.5rem] sm:gap-x-3 sm:pl-0";

function MemberRow(props: RowProps) {
  const { member, rights } = props;
  return (
    <li className="flex flex-wrap items-center gap-x-4 gap-y-3 px-4 py-3">
      <div className="flex min-w-0 flex-1 basis-60 items-center gap-3">
        <span
          aria-hidden="true"
          className="grid size-9 shrink-0 place-items-center rounded-full bg-hover text-caption font-medium text-secondary"
        >
          {memberInitials(member.name, member.email)}
        </span>
        <span className="flex min-w-0 flex-col">
          <span className="flex min-w-0 items-center gap-2">
            <span className="truncate font-medium text-primary">
              {member.name}
            </span>
            {rights.isSelf && <Badge>You</Badge>}
          </span>
          <span className="truncate text-secondary">{member.email}</span>
        </span>
      </div>
      {/* Fixed columns from sm up, so date, role and action line up from row to row. */}
      <div className={ROW_COLUMNS}>
        <span className="figure text-caption text-secondary sm:justify-self-end">
          Joined {formatDate(member.joinedAt)}
        </span>
        <div className="flex justify-center">
          <RoleControl {...props} />
        </div>
        <div className="w-26 sm:w-auto">
          <RemoveControl {...props} />
        </div>
      </div>
    </li>
  );
}

// Renders the role rules as controls: one row per member, which wraps instead of scrolling on a phone.
export default function OrgMembersTable({
  orgId,
  members,
  callerRole,
  callerUserId,
}: Props) {
  const roleOptions = assignableRoles(callerRole);
  return (
    <ul
      aria-label="Members"
      className="flex flex-col divide-y divide-line-subtle rounded-control border border-line bg-surface"
    >
      {selfFirst(members, callerUserId).map((member) => (
        <MemberRow
          key={member.membershipId}
          orgId={orgId}
          member={member}
          roleOptions={roleOptions}
          rights={rowRights(callerRole, callerUserId, member)}
        />
      ))}
    </ul>
  );
}
