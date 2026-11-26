import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { Conversation } from "@/lib/inbox-api";
import ConversationList from "./conversation-list";

const NOW = new Date("2026-06-01T12:00:00Z");
const base: Conversation = {
  id: "c1",
  channel: "INSTAGRAM",
  status: "OPEN",
  unread: false,
  participantName: "Ana Silva",
  avatarUrl: "https://cdn.example/ana.jpg",
  lastMessagePreview: "Is the blue one in stock?",
  lastMessageAt: "2026-06-01T11:55:00Z",
  assignee: null,
};
const holder = (online: boolean) => ({
  membershipId: "m1",
  userId: "u1",
  name: "Bea Costa",
  online,
});
const html = (...items: Conversation[]) =>
  renderToStaticMarkup(
    <ConversationList orgId="org-1" conversations={items} now={NOW} />,
  );

describe("ConversationList", () => {
  it("links each row to its thread and shows name, preview, channel and relative time", () => {
    const markup = html(base);
    expect(markup).toContain('href="/organizations/org-1/inbox/c1"');
    expect(markup).toContain("Ana Silva");
    expect(markup).toContain("Is the blue one in stock?");
    expect(markup).toContain("Instagram");
    expect(markup).toContain("5 minutes ago");
  });

  it("shows the customer's picture left of the name, or the initials without one", () => {
    expect(html(base)).toContain('src="https://cdn.example/ana.jpg"');
    const bare = html({ ...base, avatarUrl: null });
    expect(bare).not.toContain("<img");
    expect(bare).toContain(">AS<");
    expect(html({ ...base, avatarUrl: null, participantName: null })).toContain(
      ">?<",
    );
  });

  it("marks the open conversation and keeps the list filters in each link", () => {
    const markup = renderToStaticMarkup(
      <ConversationList
        orgId="org-1"
        conversations={[base, { ...base, id: "c2" }]}
        now={NOW}
        selectedId="c2"
        filters={{ q: "ana" }}
        listPage={1}
      />,
    );
    expect(markup).toContain(
      'href="/organizations/org-1/inbox/c1?q=ana&amp;page=1"',
    );
    expect(markup.match(/aria-current="page"/g)).toHaveLength(1);
    expect(markup).toMatch(/aria-current="page"[^>]*inbox\/c2\?/);
  });

  it("marks an unread row with a bold name and a labelled dot", () => {
    expect(html(base)).not.toContain("Unread");
    const markup = html({ ...base, unread: true });
    expect(markup).toContain("font-semibold");
    expect(markup).toContain("bg-highlight");
    expect(markup).toContain(">Unread<");
  });

  it("labels closed rows on the All tab only", () => {
    const closed = { ...base, status: "CLOSED" as const };
    const render = (tab?: "ALL" | "CLOSED") =>
      renderToStaticMarkup(
        <ConversationList
          orgId="org-1"
          conversations={[closed]}
          now={NOW}
          filters={{ tab }}
        />,
      );
    expect(render("ALL")).toContain(">Closed<");
    expect(render("CLOSED")).not.toContain(">Closed<");
  });

  it("labels a Messenger conversation", () => {
    expect(html({ ...base, channel: "MESSENGER" })).toContain("Messenger");
  });

  it("falls back when the name, preview or time is missing", () => {
    const markup = html({
      ...base,
      participantName: null,
      lastMessagePreview: null,
      lastMessageAt: null,
    });
    expect(markup).toContain("Unknown contact");
    expect(markup).toContain("No messages yet");
    expect(markup).not.toContain("<time");
  });

  describe("assignee", () => {
    it("shows an online holder's name without the offline marker", () => {
      const markup = html({ ...base, assignee: holder(true) });
      expect(markup).toContain("Bea Costa");
      expect(markup).toContain("Assigned to ");
      expect(markup).not.toContain("offline");
    });

    it("marks an open conversation held by an offline member", () => {
      expect(html({ ...base, assignee: holder(false) })).toContain("offline");
    });

    it("does not mark a closed conversation", () => {
      const markup = html({
        ...base,
        status: "CLOSED",
        assignee: holder(false),
      });
      expect(markup).toContain("Bea Costa");
      expect(markup).not.toContain("offline");
    });

    it("shows nothing extra when unassigned", () => {
      expect(html(base)).not.toContain("Assigned to");
    });
  });
});
