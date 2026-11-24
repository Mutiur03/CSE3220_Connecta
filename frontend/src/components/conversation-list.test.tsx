import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { Conversation } from "@/lib/inbox-api";
import ConversationList from "./conversation-list";

const NOW = new Date("2026-06-01T12:00:00Z");
const base: Conversation = {
  id: "c1",
  channel: "INSTAGRAM",
  participantName: "Ana Silva",
  lastMessagePreview: "Is the blue one in stock?",
  lastMessageAt: "2026-06-01T11:55:00Z",
};
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
});
