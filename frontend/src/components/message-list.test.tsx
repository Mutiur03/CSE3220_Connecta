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
const html = (props: Parameters<typeof MessageList>[0]) =>
  renderToStaticMarkup(<MessageList {...props} />);

describe("MessageList", () => {
  it("shows the oldest message first although the page is newest first", () => {
    const markup = html({
      messages: [
        msg({ id: "2", text: "second" }),
        msg({ id: "1", text: "first" }),
      ],
    });
    expect(markup.indexOf("first")).toBeLessThan(markup.indexOf("second"));
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
