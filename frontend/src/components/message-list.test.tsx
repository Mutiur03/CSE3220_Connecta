import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { Message } from "@/lib/inbox-api";
import MessageList from "./message-list";

const msg = (over: Partial<Message>): Message => ({
  id: "m",
  direction: "INBOUND",
  text: "hi",
  sentAt: "2026-06-01T10:00:00Z",
  senderId: null,
  senderName: null,
  ...over,
});
const html = (props: Omit<Parameters<typeof MessageList>[0], "now">) =>
  renderToStaticMarkup(<MessageList now={NOW} {...props} />);
const NOW = new Date("2026-06-01T12:00:00Z");

describe("MessageList", () => {
  it("shows the oldest message first although the page is newest first", () => {
    const markup = html({
      messages: [
        msg({ id: "2", text: "second" }),
        msg({ id: "1", text: "first" }),
      ],
    });
    expect(markup.indexOf(">first<")).toBeLessThan(markup.indexOf(">second<"));
  });

  it("names the agent on outbound messages only", () => {
    const markup = html({
      messages: [
        msg({ direction: "OUTBOUND", senderName: "Aavas", text: "on it" }),
        msg({ senderName: "Ana", text: "hello" }),
      ],
    });
    expect(markup).toContain("Aavas");
    expect(markup).not.toContain("Ana");
    expect(markup).toContain("bg-accent");
  });

  it("groups consecutive messages from one sender and shows the time once per group", () => {
    const markup = html({
      messages: [
        msg({
          id: "4",
          direction: "OUTBOUND",
          senderId: "u1",
          senderName: "Aavas",
          text: "yes",
          sentAt: "2026-06-01T10:10:00Z",
        }),
        msg({ id: "3", text: "and blue?", sentAt: "2026-06-01T10:02:00Z" }),
        msg({ id: "2", text: "in M?", sentAt: "2026-06-01T10:01:00Z" }),
        msg({ id: "1", text: "hi", sentAt: "2026-06-01T10:00:00Z" }),
      ],
    });
    expect(markup.match(/<li /g)).toHaveLength(2);
    expect(markup.match(/<time /g)).toHaveLength(2);
    expect(markup.match(/rounded-bl-\[4px\]/g)).toHaveLength(1);
    expect(markup.match(/rounded-br-\[4px\]/g)).toHaveLength(1);
  });

  it("puts a labelled break between days", () => {
    const markup = html({
      messages: [
        msg({ id: "2", text: "today", sentAt: "2026-06-01T10:00:00Z" }),
        msg({ id: "1", text: "before", sentAt: "2026-05-20T10:00:00Z" }),
      ],
    });
    expect(markup.match(/<h3 /g)).toHaveLength(2);
    expect(markup).toContain(">Today</h3>");
    expect(markup.indexOf("before")).toBeLessThan(markup.indexOf("today"));
  });

  it("falls back for a message without text", () => {
    expect(html({ messages: [msg({ text: null })] })).toContain("Attachment");
  });

  it("renders an empty state", () => {
    expect(html({ messages: [] })).toContain(
      "No messages in this conversation",
    );
  });

  it("links to older and newer pages only when given", () => {
    const markup = html({
      messages: [msg({})],
      olderHref: "/t?page=1",
      newerHref: "/t",
    });
    expect(markup).toContain('href="/t?page=1"');
    expect(markup).toContain("Load older messages");
    expect(markup).toContain("Show newer messages");
    expect(html({ messages: [msg({})] })).not.toContain("Load older");
  });
});
