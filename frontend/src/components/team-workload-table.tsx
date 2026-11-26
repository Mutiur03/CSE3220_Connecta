import type { MemberAvailability } from "@/lib/assignment-api";
import { setAssignableAction } from "@/lib/assignment-actions";
import { holdsChatsOffline, presenceLabel } from "@/lib/assignment-view";
import { roleLabel } from "@/lib/org-permissions";
import AssignableSwitch from "./assignable-switch";
import { tableClass } from "./org-styles";
import { TableScroll } from "./ui/table-scroll";

type Props = { orgId: string; members: MemberAvailability[] };

/** One row per member: who they are, whether they are online, whether auto-assignment may pick them, and their open load. */
export default function TeamWorkloadTable({ orgId, members }: Props) {
  return (
    <TableScroll label="Team workload">
      <table className={tableClass}>
        <thead className="border-b border-line text-secondary">
          <tr>
            <th scope="col" className="px-4 py-2 font-medium">
              Member
            </th>
            <th scope="col" className="px-4 py-2 font-medium">
              Role
            </th>
            <th scope="col" className="px-4 py-2 font-medium">
              Status
            </th>
            <th scope="col" className="px-4 py-2 font-medium">
              Assignable
            </th>
            <th scope="col" className="px-4 py-2 text-right font-medium">
              Open conversations
            </th>
          </tr>
        </thead>
        <tbody>
          {members.map((member) => (
            <tr
              key={member.membershipId}
              className="h-14 border-b border-line-subtle last:border-b-0 hover:bg-hover/60"
            >
              <td className="px-4 py-2 align-middle">
                <span className="flex flex-col">
                  <span className="font-medium">{member.name}</span>
                  <span className="text-caption text-secondary">
                    {member.email}
                  </span>
                </span>
              </td>
              <td className="px-4 py-2 align-middle text-secondary">
                {roleLabel(member.role)}
              </td>
              <td className="px-4 py-2 align-middle">
                <span className="inline-flex items-center gap-1.5">
                  <span
                    aria-hidden="true"
                    className={`size-1.5 rounded-full ${member.online === true ? "bg-highlight" : "bg-line-strong"}`}
                  />
                  {presenceLabel(member)}
                </span>
              </td>
              <td className="px-4 py-2 align-middle">
                <AssignableSwitch
                  action={setAssignableAction.bind(
                    null,
                    orgId,
                    member.membershipId,
                  )}
                  assignable={member.assignable === true}
                  name={member.name}
                />
              </td>
              <td className="figure px-4 py-2 text-right align-middle">
                {member.openConversations}
                {holdsChatsOffline(member) && (
                  <span className="font-sans text-caption text-secondary">
                    {" "}
                    open, offline
                  </span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </TableScroll>
  );
}
