import { describe, expect, it } from "vitest";
import type { AssignmentRecord, MemberAvailability } from "./assignment-api";
import {
  assigneeBadgeLabel,
  historyLine,
  holdsChatsOffline,
  memberOptionLabel,
  presenceLabel,
} from "./assignment-view";

const person = (name: string, membershipId: string | null = "m") => ({
  membershipId,
  userId: "u",
  name,
});

const record = (over: Partial<AssignmentRecord>): AssignmentRecord => ({
  id: "r1",
  from: null,
  to: null,
  assignedBy: null,
  source: "MANUAL",
  createdAt: "2026-11-26T10:00:00Z",
  ...over,
});

describe("historyLine", () => {
  it("names the first assignment and who made it", () => {
    expect(
      historyLine(
        record({ to: person("Mira Das"), assignedBy: person("Ana Roy", null) }),
      ),
    ).toBe("Assigned to Mira Das by Ana Roy");
  });

  it("describes a move between members", () => {
    expect(
      historyLine(
        record({
          from: person("Mira Das"),
          to: person("Sam Lee"),
          assignedBy: person("Ana Roy", null),
        }),
      ),
    ).toBe("Moved from Mira Das to Sam Lee by Ana Roy");
  });

  it("describes an unassignment", () => {
    expect(
      historyLine(
        record({
          from: person("Mira Das"),
          assignedBy: person("Ana Roy", null),
        }),
      ),
    ).toBe("Unassigned from Mira Das by Ana Roy");
  });

  it("says automatically for round-robin", () => {
    expect(
      historyLine(
        record({ to: person("Sam Lee"), source: "AUTO_ROUND_ROBIN" }),
      ),
    ).toBe("Assigned to Sam Lee automatically");
  });
});

const member = (over: Partial<MemberAvailability>) =>
  ({
    membershipId: "m1",
    name: "Mira Das",
    online: true,
    assignable: true,
    openConversations: 3,
    ...over,
  }) as MemberAvailability;

describe("presence helpers", () => {
  it("labels a member Online or Offline, treating a missing flag as offline", () => {
    expect(presenceLabel(member({ online: true }))).toBe("Online");
    expect(presenceLabel(member({ online: false }))).toBe("Offline");
    expect(presenceLabel(member({ online: undefined }))).toBe("Offline");
  });

  it("flags only an offline member who holds open conversations", () => {
    expect(holdsChatsOffline(member({ online: false }))).toBe(true);
    expect(holdsChatsOffline(member({ online: true }))).toBe(false);
    expect(
      holdsChatsOffline(member({ online: false, openConversations: 0 })),
    ).toBe(false);
  });
});

describe("memberOptionLabel", () => {
  it("shows presence and load", () => {
    expect(memberOptionLabel(member({}))).toBe("Mira Das (Online, 3 open)");
  });

  it("marks a member auto-assignment skips", () => {
    expect(
      memberOptionLabel(member({ online: false, assignable: false })),
    ).toBe("Mira Das (Offline, 3 open, not assignable)");
  });
});

describe("assigneeBadgeLabel", () => {
  const assignee = { membershipId: "m1", name: "Mira Das" };

  it("adds offline when the team list says the assignee is offline", () => {
    expect(assigneeBadgeLabel(assignee, [member({ online: false })])).toBe(
      "Assigned to Mira Das · offline",
    );
  });

  it("stays plain for an online assignee, an unknown one or no team list", () => {
    expect(assigneeBadgeLabel(assignee, [member({})])).toBe(
      "Assigned to Mira Das",
    );
    expect(assigneeBadgeLabel(assignee, [member({ membershipId: "x" })])).toBe(
      "Assigned to Mira Das",
    );
    expect(assigneeBadgeLabel(assignee, null)).toBe("Assigned to Mira Das");
  });
});
