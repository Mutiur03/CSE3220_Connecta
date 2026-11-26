import { describe, expect, it } from "vitest";
import type { AssignmentRecord, MemberAvailability } from "./assignment-api";
import {
  historyLine,
  isAvailabilityStatus,
  memberOptionLabel,
  otherStatus,
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

describe("status helpers", () => {
  it("flips the status and validates it", () => {
    expect(otherStatus("AVAILABLE")).toBe("AWAY");
    expect(otherStatus("AWAY")).toBe("AVAILABLE");
    expect(isAvailabilityStatus("AWAY")).toBe(true);
    expect(isAvailabilityStatus("busy")).toBe(false);
  });

  it("labels a member option with status and load", () => {
    const member = {
      name: "Mira Das",
      status: "AVAILABLE",
      openConversations: 3,
    } as MemberAvailability;
    expect(memberOptionLabel(member)).toBe("Mira Das (Available, 3 open)");
  });
});
