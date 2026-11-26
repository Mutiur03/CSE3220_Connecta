import { OWNER_ROLE, ROLE_LABELS, type OrgRole } from "@/lib/org-permissions";
import { setUserEnabled } from "@/lib/user-actions";
import type { AdminUser } from "@/lib/user-api";
import OrgActionButton from "./org-action-button";
import { tableClass } from "./org-styles";
import { Badge } from "./ui/badge";
import { TableScroll } from "./ui/table-scroll";

type Props = { users: AdminUser[]; callerUserId: string };

const roleLabel = (roles: string[]) =>
  roles.map((role) => ROLE_LABELS[role as OrgRole] ?? role).join(", ");

function RowAction({ user, isSelf }: { user: AdminUser; isSelf: boolean }) {
  if (isSelf) return <span className="text-secondary">You</span>;
  if (user.roles.includes(OWNER_ROLE))
    return <span className="text-secondary">Owner</span>;
  if (!user.enabled)
    return (
      <OrgActionButton
        action={setUserEnabled.bind(null, user.id, true)}
        label="Enable"
        pendingLabel="Enabling…"
        ariaLabel={`Enable ${user.fullName}`}
        size="sm"
      />
    );
  return (
    <OrgActionButton
      action={setUserEnabled.bind(null, user.id, false)}
      label="Disable"
      pendingLabel="Disabling…"
      ariaLabel={`Disable ${user.fullName}`}
      confirm={`Disable ${user.fullName}? They are signed out right away and cannot sign in until you enable them again.`}
      tone="danger"
      size="sm"
    />
  );
}

/** Accounts as rows; your own row and other owners have no Disable (the backend refuses it). */
export default function UserTable({ users, callerUserId }: Props) {
  return (
    <TableScroll label="User accounts">
      <table className={tableClass}>
        <thead className="border-b border-line text-secondary">
          <tr>
            <th scope="col" className="px-4 py-2 font-medium">
              Email
            </th>
            <th scope="col" className="px-4 py-2 font-medium">
              Name
            </th>
            <th scope="col" className="px-4 py-2 font-medium">
              Role
            </th>
            <th scope="col" className="px-4 py-2 font-medium">
              Status
            </th>
            <th scope="col" className="px-4 py-2 text-right font-medium">
              <span className="sr-only">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {users.map((user) => (
            <tr
              key={user.id}
              className="h-14 border-b border-line-subtle last:border-b-0 hover:bg-hover/60"
            >
              <td className="px-4 py-2 align-middle">{user.email}</td>
              <td className="px-4 py-2 align-middle">{user.fullName}</td>
              <td className="px-4 py-2 align-middle text-secondary">
                {roleLabel(user.roles)}
              </td>
              <td className="px-4 py-2 align-middle">
                <Badge tone={user.enabled ? "success" : "neutral"}>
                  {user.enabled ? "Active" : "Disabled"}
                </Badge>
              </td>
              <td className="px-4 py-2 align-middle">
                <div className="flex justify-end">
                  <RowAction user={user} isSelf={user.id === callerUserId} />
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </TableScroll>
  );
}
