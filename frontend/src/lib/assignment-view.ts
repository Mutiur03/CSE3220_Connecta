import type {
  AssignmentRecord,
  AvailabilityStatus,
  MemberAvailability,
} from "./assignment-api";

export const AVAILABILITY_LABELS: Record<AvailabilityStatus, string> = {
  AVAILABLE: "Available",
  AWAY: "Away",
};

export const otherStatus = (status: AvailabilityStatus): AvailabilityStatus =>
  status === "AVAILABLE" ? "AWAY" : "AVAILABLE";

export const isAvailabilityStatus = (
  value: unknown,
): value is AvailabilityStatus => value === "AVAILABLE" || value === "AWAY";

/** The value of the assign select that means "no assignee". */
export const UNASSIGNED = "";

/** "Mira Das (Available, 3 open)": enough to pick the least loaded available agent. */
export function memberOptionLabel(member: MemberAvailability): string {
  return `${member.name} (${AVAILABILITY_LABELS[member.status]}, ${member.openConversations} open)`;
}

/** One history line: who moved the conversation where, and how. */
export function historyLine(record: AssignmentRecord): string {
  const to = record.to?.name;
  const from = record.from?.name;
  const move = to
    ? from
      ? `Moved from ${from} to ${to}`
      : `Assigned to ${to}`
    : from
      ? `Unassigned from ${from}`
      : "Unassigned";
  const by =
    record.source === "AUTO_ROUND_ROBIN"
      ? "automatically"
      : record.assignedBy
        ? `by ${record.assignedBy.name}`
        : "manually";
  return `${move} ${by}`;
}
