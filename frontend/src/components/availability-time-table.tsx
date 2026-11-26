import type { MemberAvailabilityReport } from "@/lib/availability-report-api";
import { dayHeading, formatDuration } from "@/lib/availability-report-view";
import { roleLabel } from "@/lib/org-permissions";
import { tableClass } from "./org-styles";
import { TableScroll } from "./ui/table-scroll";

type Props = {
  members: MemberAvailabilityReport[];
  /** The report's dates, in order, for the column headings. */
  dates: string[];
  /** Today's UTC date; its column is tinted. */
  today: string;
};

/** Members down, UTC days across, available time in each cell and a total at the end. */
export default function AvailabilityTimeTable({
  members,
  dates,
  today,
}: Props) {
  if (members.length === 0) {
    return (
      <p className="text-secondary">This organization has no members yet.</p>
    );
  }
  return (
    <TableScroll label="Time available per day">
      <table className={`${tableClass} min-w-max`}>
        <caption className="sr-only">
          Time each member was available, per UTC day
        </caption>
        <thead className="border-b border-line text-secondary">
          <tr>
            <th scope="col" className="px-4 py-2 font-medium">
              Member
            </th>
            {dates.map((date) => (
              <th
                key={date}
                scope="col"
                aria-current={date === today ? "date" : undefined}
                className={`px-4 py-2 text-right font-medium whitespace-nowrap ${date === today ? "bg-selected text-primary" : ""}`}
              >
                {dayHeading(date)}
              </th>
            ))}
            <th scope="col" className="px-4 py-2 text-right font-medium">
              Total
            </th>
          </tr>
        </thead>
        <tbody>
          {members.map((member) => (
            <tr
              key={member.membershipId}
              className="h-14 border-b border-line-subtle last:border-b-0 hover:bg-hover/60"
            >
              <th
                scope="row"
                className="px-4 py-2 text-left align-middle font-normal"
              >
                <span className="flex flex-col">
                  <span className="font-medium">{member.name}</span>
                  <span className="text-caption text-secondary">
                    {roleLabel(member.role)}
                  </span>
                </span>
              </th>
              {member.days.map((day) => (
                <td
                  key={day.date}
                  className={`figure px-4 py-2 text-right align-middle whitespace-nowrap ${day.date === today ? "bg-selected" : ""} ${day.availableSeconds > 0 ? "" : "text-secondary"}`}
                >
                  {formatDuration(day.availableSeconds)}
                </td>
              ))}
              <td className="figure px-4 py-2 text-right align-middle font-medium whitespace-nowrap">
                {formatDuration(member.totalSeconds)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </TableScroll>
  );
}
