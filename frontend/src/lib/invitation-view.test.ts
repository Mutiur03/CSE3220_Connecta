import { describe, expect, it } from "vitest";
import { invitationBadgeTone } from "./invitation-view";

describe("invitationBadgeTone", () => {
  it.each([
    ["Pending", "accent"],
    ["Accepted", "success"],
    ["Revoked", "neutral"],
    ["Expired", "neutral"],
    ["Unknown", "neutral"],
  ])("maps %s to %s", (label, tone) => {
    expect(invitationBadgeTone(label)).toBe(tone);
  });
});
