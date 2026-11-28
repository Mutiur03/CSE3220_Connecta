import type { AgentAnalytics } from "@/lib/analytics-api";
import { formatSeconds } from "@/lib/analytics-view";
import { roleLabel } from "@/lib/org-permissions";
import { tableClass } from "./org-styles";
import { TableScroll } from "./ui/table-scroll";

const NUMBER_COLUMNS = [
  "Assigned",
  "Resolved",
  "Open now",
  "Replies sent",
  "Time available",
  "Median first response",
] as const;

/** One row per member with their load and response in the range. */
export default function AgentAnalyticsTable({
  agents,
}: {
  agents: AgentAnalytics[];
}) {
  return (
    <TableScroll label="Agent performance">
      <table className={tableClass}>
        <thead className="border-b border-line text-secondary">
          <tr>
            <th scope="col" className="px-4 py-2 font-medium">
              Member
            </th>
            {NUMBER_COLUMNS.map((column) => (
              <th
                key={column}
                scope="col"
                className="px-4 py-2 text-right font-medium"
              >
                {column}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {agents.map((agent) => (
            <tr
              key={agent.membershipId}
              className="h-14 border-b border-line-subtle last:border-b-0 hover:bg-hover/60"
            >
              <th
                scope="row"
                className="px-4 py-2 text-left align-middle font-normal"
              >
                <span className="flex flex-col">
                  <span className="font-medium">{agent.name}</span>
                  <span className="text-caption text-secondary">
                    {roleLabel(agent.role)}
                  </span>
                </span>
              </th>
              <td className="figure px-4 py-2 text-right align-middle">
                {agent.assigned}
              </td>
              <td className="figure px-4 py-2 text-right align-middle">
                {agent.resolved}
              </td>
              <td className="figure px-4 py-2 text-right align-middle">
                {agent.openNow}
              </td>
              <td className="figure px-4 py-2 text-right align-middle">
                {agent.repliesSent}
              </td>
              <td className="figure px-4 py-2 text-right align-middle">
                {formatSeconds(agent.availableSeconds)}
              </td>
              <td className="figure px-4 py-2 text-right align-middle">
                {formatSeconds(agent.medianFirstResponseSeconds)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </TableScroll>
  );
}
