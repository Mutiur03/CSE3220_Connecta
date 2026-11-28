import { describe, expect, it } from "vitest";
import {
  barPercent,
  formatSeconds,
  parseAnalyticsRange,
  parseZone,
  shortDate,
  todayIn,
} from "./analytics-view";

describe("parseZone", () => {
  it("keeps a known IANA zone", () => {
    expect(parseZone("Asia/Dhaka")).toBe("Asia/Dhaka");
  });

  it("drops a missing or unknown zone", () => {
    expect(parseZone(undefined)).toBeUndefined();
    expect(parseZone("Mars/Olympus")).toBeUndefined();
  });
});

describe("todayIn", () => {
  it("is the reader's calendar day, not the server's", () => {
    const now = new Date("2026-11-28T20:00:00Z");
    expect(todayIn("Asia/Dhaka", now).toISOString().slice(0, 10)).toBe(
      "2026-11-29",
    );
    expect(todayIn("UTC", now).toISOString().slice(0, 10)).toBe("2026-11-28");
  });
});

describe("parseAnalyticsRange", () => {
  const today = new Date("2026-11-28T00:00:00Z");

  it("defaults to the last 30 days", () => {
    expect(parseAnalyticsRange(undefined, undefined, today)).toEqual({
      from: "2026-10-30",
      to: "2026-11-28",
    });
  });

  it("trims a range over 366 days to its last 366", () => {
    expect(parseAnalyticsRange("2024-01-01", "2026-11-28", today).from).toBe(
      "2025-11-28",
    );
  });
});

describe("formatSeconds", () => {
  it.each([
    [null, "–"],
    [0, "–"],
    [45, "45s"],
    [540, "9m"],
    [302400, "84h 0m"],
  ])("formats %s as %s", (seconds, text) => {
    expect(formatSeconds(seconds)).toBe(text);
  });
});

describe("barPercent", () => {
  it("scales to the largest value and keeps a sliver for small counts", () => {
    expect(barPercent(50, 100)).toBe(50);
    expect(barPercent(1, 1000)).toBe(2);
    expect(barPercent(0, 10)).toBe(0);
    expect(barPercent(0, 0)).toBe(0);
  });
});

describe("shortDate", () => {
  it("reads the ISO date as a calendar day", () => {
    expect(shortDate("2026-11-03")).toBe("Nov 3");
  });
});
