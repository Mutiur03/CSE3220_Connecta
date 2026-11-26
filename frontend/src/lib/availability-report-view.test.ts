import { describe, expect, it } from "vitest";
import {
  dayHeading,
  formatDuration,
  lastDays,
  parseRange,
} from "./availability-report-view";

const today = new Date("2026-11-27T23:30:00Z");
const week = { from: "2026-11-21", to: "2026-11-27" };

describe("formatDuration", () => {
  it.each([
    [0, "–"],
    [59, "–"],
    [60, "1m"],
    [3599, "59m"],
    [3600, "1h 0m"],
    [8100, "2h 15m"],
    [86400, "24h 0m"],
  ])("%i seconds reads %s", (seconds, text) => {
    expect(formatDuration(seconds)).toBe(text);
  });
});

describe("lastDays", () => {
  it("ends today in UTC and counts today", () => {
    expect(lastDays(today, 7)).toEqual(week);
    expect(lastDays(today, 30)).toEqual({
      from: "2026-10-29",
      to: "2026-11-27",
    });
  });
});

describe("parseRange", () => {
  it("defaults to the last 7 days with no params", () => {
    expect(parseRange(undefined, undefined, today)).toEqual(week);
  });

  it("keeps a valid range", () => {
    expect(parseRange("2026-11-01", "2026-11-10", today)).toEqual({
      from: "2026-11-01",
      to: "2026-11-10",
    });
  });

  it("trims a range over 31 days, keeping the end date", () => {
    expect(parseRange("2026-01-01", "2026-11-10", today)).toEqual({
      from: "2026-10-11",
      to: "2026-11-10",
    });
  });

  it("allows exactly 31 days", () => {
    expect(parseRange("2026-10-11", "2026-11-10", today).from).toBe(
      "2026-10-11",
    );
  });

  it.each([
    ["garbage", "2026-11-10"],
    ["2026-02-31", "2026-11-10"],
    ["2026-11-10", ""],
    ["2026-11-10", "2026-11-01"],
  ])("falls back to the default for %s to %s", (from, to) => {
    expect(parseRange(from, to, today)).toEqual(week);
  });

  it("uses the first value of a repeated param", () => {
    expect(parseRange(["2026-11-01", "x"], "2026-11-10", today).from).toBe(
      "2026-11-01",
    );
  });
});

describe("dayHeading", () => {
  it("gives the UTC weekday and day of month", () => {
    expect(dayHeading("2026-11-24")).toBe("Tue 24");
  });
});
