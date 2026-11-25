import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { Attachment, Message } from "@/lib/inbox-api";
import MessageList from "./message-list";

const msg = (over: Partial<Message>): Message => ({
  id: "m",
  direction: "INBOUND",
  text: "hi",
  sentAt: "2026-06-01T10:00:00Z",
  senderId: null,
  senderName: null,
  attachments: [],
  ...over,
});
const file = (over: Partial<Attachment>): Attachment => ({
  id: "a",
  kind: "IMAGE",
  url: "https://cdn.example/media/1",
  fileName: null,
  contentType: null,
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

  describe("attachments", () => {
    it("shows a photo lazily inside a link that opens it in a new tab, without the label text", () => {
      const markup = html({
        messages: [
          msg({
            text: "[Photo]",
            senderName: "Nadia",
            attachments: [file({})],
          }),
        ],
      });
      expect(markup).toMatch(
        /<a href="https:\/\/cdn.example\/media\/1" target="_blank" rel="noopener noreferrer"[^>]*><img [^>]*alt="Photo from Nadia"[^>]*loading="lazy"/,
      );
      expect(markup).not.toContain("[Photo]");
      expect(markup).not.toContain("Attachment or unsupported");
    });

    it("uses the file name as alt text when there is one", () => {
      const markup = html({
        messages: [msg({ attachments: [file({ fileName: "receipt.jpg" })] })],
      });
      expect(markup).toContain('alt="receipt.jpg"');
    });

    it("plays video and audio with native controls", () => {
      const markup = html({
        messages: [
          msg({
            text: "[Video]",
            attachments: [
              file({ id: "v", kind: "VIDEO" }),
              file({ id: "s", kind: "AUDIO" }),
            ],
          }),
        ],
      });
      expect(markup).toMatch(/<video [^>]*controls=""[^>]*preload="metadata"/);
      expect(markup).toMatch(/<audio [^>]*controls=""/);
      expect(markup).toContain('aria-label="Voice message from the customer"');
    });

    it("links a file by its name", () => {
      const markup = html({
        messages: [
          msg({
            text: "[Attachment]",
            attachments: [file({ kind: "FILE", fileName: "invoice.pdf" })],
          }),
        ],
      });
      expect(markup).toMatch(
        /<a [^>]*rel="noopener noreferrer"[^>]*>.*invoice\.pdf/,
      );
      expect(markup).not.toContain("[Attachment]");
    });

    it("shows a shared post as one link card that opens Instagram in a new tab, without the label", () => {
      const markup = html({
        messages: [
          msg({
            text: "[Shared post]",
            attachments: [
              file({
                kind: "LINK",
                url: "https://www.instagram.com/p/C8xYz/",
                fileName: "Shared post",
              }),
            ],
          }),
        ],
      });
      expect(markup).toMatch(
        /<a href="https:\/\/www.instagram.com\/p\/C8xYz\/" target="_blank" rel="noopener noreferrer" aria-label="Shared post on Instagram \(opens in a new tab\)"/,
      );
      expect(markup).toContain(">www.instagram.com<");
      expect(markup).toContain("<svg");
      expect(markup).not.toContain("[Shared post]");
    });

    it("hides the story mention label and falls back to a generic title", () => {
      const markup = html({
        messages: [
          msg({
            text: "[Story mention]",
            attachments: [
              file({
                kind: "LINK",
                url: "https://www.instagram.com/stories/nadia/1/",
              }),
            ],
          }),
        ],
      });
      expect(markup).not.toContain("[Story mention]");
      expect(markup).toContain(
        'aria-label="Instagram post on Instagram (opens in a new tab)"',
      );
    });

    it("keeps real text next to a link card", () => {
      const markup = html({
        messages: [
          msg({
            text: "Do you have this in blue?",
            attachments: [
              file({ kind: "LINK", url: "https://www.instagram.com/p/1/" }),
            ],
          }),
        ],
      });
      expect(markup).toContain("Do you have this in blue?");
    });

    it("shows the title as plain text when the link is not http(s)", () => {
      const markup = html({
        messages: [
          msg({
            text: "[Shared post]",
            attachments: [
              file({
                kind: "LINK",
                url: "javascript:alert(1)",
                fileName: "Shared post",
              }),
            ],
          }),
        ],
      });
      expect(markup).toContain("Shared post");
      expect(markup).not.toContain("<a ");
      expect(markup).not.toContain("javascript:");
    });

    it("keeps a real caption next to the media", () => {
      const markup = html({
        messages: [
          msg({ text: "Is this one in stock?", attachments: [file({})] }),
        ],
      });
      expect(markup).toContain("Is this one in stock?");
    });
  });
});
