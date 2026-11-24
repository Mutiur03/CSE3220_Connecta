import { describe, expect, it } from "vitest";
import {
  hasInboxFilters,
  inboxHref,
  inboxListQuery,
  parseInboxFilters,
  participantLabel,
  UNKNOWN_PARTICIPANT,
} from "./inbox-view";

describe("parseInboxFilters", () => {
  it("keeps a known channel and a trimmed search", () => {
    expect(parseInboxFilters({ q: "  ana ", channel: "INSTAGRAM" })).toEqual({
      q: "ana",
      channel: "INSTAGRAM",
    });
  });

  it("drops an unknown channel and a blank search", () => {
    expect(parseInboxFilters({ q: "  ", channel: "SMS" })).toEqual({
      q: undefined,
      channel: undefined,
    });
  });

  it("uses the first of repeated params and caps the search length", () => {
    expect(parseInboxFilters({ q: ["a", "b"] }).q).toBe("a");
    expect(parseInboxFilters({ q: "x".repeat(500) }).q).toHaveLength(100);
  });
});

describe("inboxListQuery and inboxHref", () => {
  it("leaves out unset filters and page 0", () => {
    expect(inboxListQuery({})).toBe("");
    expect(inboxHref("/inbox", {})).toBe("/inbox");
  });

  it("writes filters, page and size", () => {
    expect(inboxListQuery({ q: "a b", channel: "MESSENGER" }, 2, 20)).toBe(
      "q=a+b&channel=MESSENGER&page=2&size=20",
    );
    expect(inboxHref("/inbox", { channel: "MESSENGER" }, 1)).toBe(
      "/inbox?channel=MESSENGER&page=1",
    );
  });
});

describe("hasInboxFilters", () => {
  it("is true only when a filter is set", () => {
    expect(hasInboxFilters({})).toBe(false);
    expect(hasInboxFilters({ q: "a" })).toBe(true);
    expect(hasInboxFilters({ channel: "INSTAGRAM" })).toBe(true);
  });
});

describe("participantLabel", () => {
  it("falls back when the name is missing or blank", () => {
    expect(participantLabel(null)).toBe(UNKNOWN_PARTICIPANT);
    expect(participantLabel("  ")).toBe(UNKNOWN_PARTICIPANT);
    expect(participantLabel("Ana")).toBe("Ana");
  });
});

import {
  nextStatus,
  participantsLabel,
  readingOrder,
  threadHref,
} from "./inbox-view";

describe("thread helpers", () => {
  it("reverses without mutating", () => {
    const input = [3, 2, 1];
    expect(readingOrder(input)).toEqual([1, 2, 3]);
    expect(input).toEqual([3, 2, 1]);
  });

  it("joins names or falls back", () => {
    expect(
      participantsLabel([{ displayName: "Ana" }, { displayName: " Bo " }]),
    ).toBe("Ana, Bo");
    expect(participantsLabel([{ displayName: null }])).toBe(
      UNKNOWN_PARTICIPANT,
    );
  });

  it("builds thread links, page 0 clean", () => {
    expect(threadHref("/t")).toBe("/t");
    expect(threadHref("/t", 2)).toBe("/t?page=2");
  });

  it("toggles status", () => {
    expect(nextStatus("OPEN")).toBe("CLOSED");
    expect(nextStatus("CLOSED")).toBe("OPEN");
  });
});
