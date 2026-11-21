import { describe, expect, it } from "vitest";
import { formatDateTime, invitationStatusLabel } from "./format";

describe("invitationStatusLabel", () => {
  const now = new Date("2026-06-01T00:00:00Z");
  const future = "2026-07-01T00:00:00Z";
  const past = "2026-05-01T00:00:00Z";

  it("shows a pending invitation as pending until it expires", () => {
    expect(invitationStatusLabel("PENDING", future, now)).toBe("Pending");
    expect(invitationStatusLabel("PENDING", past, now)).toBe("Expired");
  });

  it("shows accepted and revoked as they are, whatever the expiry", () => {
    expect(invitationStatusLabel("ACCEPTED", past, now)).toBe("Accepted");
    expect(invitationStatusLabel("REVOKED", future, now)).toBe("Revoked");
  });
});

describe("formatDateTime", () => {
  it("shows the date and the clock time", () => {
    const text = formatDateTime("2026-03-04T09:30:00Z");
    expect(text).toContain("2026");
    expect(text).toMatch(/\d{1,2}:\d{2}/);
  });
});
