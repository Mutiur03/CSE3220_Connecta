import { describe, expect, it } from "vitest";
import {
  avatarInitials,
  avatarTone,
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

  it("builds thread links that keep the list filters, page 0 clean", () => {
    expect(threadHref("/t")).toBe("/t");
    expect(threadHref("/t", {}, 0, 2)).toBe("/t?history=2");
    expect(threadHref("/t", { q: "ana", channel: "INSTAGRAM" }, 1)).toBe(
      "/t?q=ana&channel=INSTAGRAM&page=1",
    );
  });

  it("toggles status", () => {
    expect(nextStatus("OPEN")).toBe("CLOSED");
    expect(nextStatus("CLOSED")).toBe("OPEN");
  });
});

describe("avatarInitials and avatarTone", () => {
  it("takes up to two initials", () => {
    expect(avatarInitials("ana maria silva")).toBe("AM");
    expect(avatarInitials("  Rina ")).toBe("R");
  });

  it("is ? without a usable name", () => {
    expect(avatarInitials(null)).toBe("?");
    expect(avatarInitials("   ")).toBe("?");
  });

  it("keeps a person on one neutral token", () => {
    expect(avatarTone("Ana Silva")).toBe(avatarTone("Ana Silva"));
    expect(["bg-hover", "bg-selected"]).toContain(avatarTone("Bob"));
    expect(["bg-hover", "bg-selected"]).toContain(avatarTone(null));
  });
});

import { dayLabel, groupThread } from "./inbox-view";

describe("groupThread", () => {
  const NOW = new Date("2026-06-01T12:00:00Z");
  const m = (
    id: string,
    sentAt: string,
    direction: "INBOUND" | "OUTBOUND" = "INBOUND",
    senderId: string | null = null,
  ) => ({ id, sentAt, direction, senderId, senderName: null });

  it("joins same-sender messages within five minutes and splits on a longer gap", () => {
    const [day] = groupThread(
      [
        m("1", "2026-06-01T10:00:00Z"),
        m("2", "2026-06-01T10:04:00Z"),
        m("3", "2026-06-01T10:20:00Z"),
      ],
      NOW,
    );
    expect(day.groups.map((g) => g.messages.map((x) => x.id))).toEqual([
      ["1", "2"],
      ["3"],
    ]);
  });

  it("splits when the side or the agent changes", () => {
    const [day] = groupThread(
      [
        m("1", "2026-06-01T10:00:00Z"),
        m("2", "2026-06-01T10:01:00Z", "OUTBOUND", "a"),
        m("3", "2026-06-01T10:02:00Z", "OUTBOUND", "b"),
      ],
      NOW,
    );
    expect(day.groups).toHaveLength(3);
    expect(day.groups[1].outbound).toBe(true);
  });

  it("starts a new day with its own label", () => {
    const days = groupThread(
      [m("1", "2026-05-20T10:00:00Z"), m("2", "2026-06-01T10:00:00Z")],
      NOW,
    );
    expect(days).toHaveLength(2);
    expect(days[1].label).toBe("Today");
  });

  it("labels today, yesterday, then the date", () => {
    expect(dayLabel("2026-06-01T09:00:00Z", NOW)).toBe("Today");
    expect(dayLabel("2026-05-31T09:00:00Z", NOW)).toBe("Yesterday");
    expect(dayLabel("2026-05-20T09:00:00Z", NOW)).toContain("May 20");
  });
});
