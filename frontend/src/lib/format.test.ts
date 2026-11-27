import { describe, expect, it } from "vitest";
import {
  formatDate,
  formatDateTime,
  formatRelative,
  formatTime,
  invitationStatusLabel,
} from "./format";

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

describe("formatRelative", () => {
  const now = new Date("2026-11-24T12:00:00Z");

  it.each([
    ["2026-11-24T11:59:40Z", "just now"],
    ["2026-11-24T11:55:00Z", "5 minutes ago"],
    ["2026-11-24T09:00:00Z", "3 hours ago"],
    ["2026-11-23T11:00:00Z", "yesterday"],
    ["2026-11-20T12:00:00Z", "4 days ago"],
  ])("%s -> %s", (iso, text) => {
    expect(formatRelative(iso, now)).toBe(text);
  });

  it("falls back to a date after a week", () => {
    expect(formatRelative("2026-11-01T12:00:00Z", now)).toBe("Nov 1, 2026");
  });
});

describe("the clock formatters and a time zone", () => {
  const moment = "2026-11-27T22:03:00Z";

  it("shows the clock in the zone it is given", () => {
    expect(formatTime(moment, "UTC")).toMatch(/^10:03\sPM$/);
    expect(formatTime(moment, "Asia/Dhaka")).toMatch(/^4:03\sAM$/);
  });

  it("moves the day with the zone, so a late evening in UTC is already tomorrow in Dhaka", () => {
    expect(formatDate(moment, "UTC")).toBe("Nov 27, 2026");
    expect(formatDate(moment, "Asia/Dhaka")).toBe("Nov 28, 2026");
    expect(formatDateTime(moment, "Asia/Dhaka")).toMatch(
      /Nov 28, 2026.*4:03\sAM/,
    );
  });
});
