import type { MemberAvailability } from "@/lib/assignment-api";
import { AVAILABILITY_LABELS } from "@/lib/assignment-view";
import { formatRelative } from "@/lib/format";
import { roleLabel } from "@/lib/org-permissions";
import { tableClass } from "./org-styles";
import { Badge } from "./ui/badge";
import { TableScroll } from "./ui/table-scroll";

type Props = { members: MemberAvailability[]; now: Date };

/** One row per member: who they are, whether they take new conversations, and how many open ones they hold. */
export default function TeamWorkloadTable({ members, now }: Props) {
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
              Availability
            </th>
            <th scope="col" className="px-4 py-2 text-right font-medium">
              Open conversations
            </th>
            <th scope="col" className="px-4 py-2 text-right font-medium">
              Updated
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
                <Badge
                  tone={member.status === "AVAILABLE" ? "success" : "neutral"}
                  dot={member.status === "AVAILABLE"}
                >
                  {AVAILABILITY_LABELS[member.status]}
                </Badge>
              </td>
              <td className="figure px-4 py-2 text-right align-middle">
                {member.openConversations}
              </td>
              <td className="figure px-4 py-2 text-right align-middle text-secondary">
                {member.updatedAt
                  ? formatRelative(member.updatedAt, now)
                  : "Never set"}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </TableScroll>
  );
}
