import type { AssignmentRecord, MemberAvailability } from "./assignment-api";

/** "Online" or "Offline". A member the backend did not flag counts as offline. */
export const presenceLabel = (member: Pick<MemberAvailability, "online">) =>
  member.online === true ? "Online" : "Offline";

/** An offline member who still holds open conversations: those chats wait for someone who is not here. */
export const holdsChatsOffline = (
  member: Pick<MemberAvailability, "online" | "openConversations">,
) => member.online !== true && member.openConversations > 0;

/** The value of the assign select that means "no assignee". */
export const UNASSIGNED = "";

/** "Mira Das (Online, 3 open)", plus "not assignable" when auto-assignment skips them. */
export function memberOptionLabel(member: MemberAvailability): string {
  const parts = [presenceLabel(member), `${member.openConversations} open`];
  if (member.assignable !== true) parts.push("not assignable");
  return `${member.name} (${parts.join(", ")})`;
}

/** The thread badge: "Assigned to Mira Das", with " · offline" when the admin's team list says they are away. */
export function assigneeBadgeLabel(
  assignee: { membershipId: string; name: string },
  team: MemberAvailability[] | null,
): string {
  const member = team?.find((m) => m.membershipId === assignee.membershipId);
  const offline = member !== undefined && member.online !== true;
  return `Assigned to ${assignee.name}${offline ? " · offline" : ""}`;
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
