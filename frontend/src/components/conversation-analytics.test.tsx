import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { ConversationAnalytics } from "@/lib/analytics-api";
import ConversationAnalyticsReport from "./conversation-analytics";

const report: ConversationAnalytics = {
  from: "2026-11-01",
  to: "2026-11-03",
  zone: "Asia/Dhaka",
  started: 9,
  startedPerDay: [
    { date: "2026-11-01", count: 2 },
    { date: "2026-11-02", count: 0 },
    { date: "2026-11-03", count: 7 },
  ],
  startedPerChannel: [
    { channel: "INSTAGRAM", count: 6 },
    { channel: "MESSENGER", count: 3 },
  ],
  statusNow: { open: 37, closed: 410 },
  messagesReceived: 1210,
  messagesSent: 980,
  medianFirstResponseSeconds: 540,
  unanswered: 2,
};

const render = (value: ConversationAnalytics) =>
  renderToStaticMarkup(<ConversationAnalyticsReport report={value} />);

describe("ConversationAnalyticsReport", () => {
  it("shows the headline numbers with the duration formatted", () => {
    const markup = render(report);
    expect(markup).toContain("Conversations started");
    expect(markup).toContain(">1,210<");
    expect(markup).toContain(">9m<");
    expect(markup).toContain("410 closed, all time");
  });

  it("sums the daily chart up in text and keeps every day in a table", () => {
    const markup = render(report);
    expect(markup).toContain(
      "Busiest day Nov 3 with 7. Days run on Asia/Dhaka.",
    );
    expect(markup).toMatch(
      /<th scope="row"[^>]*>2026-11-02<\/th><td[^>]*>0<\/td>/,
    );
    expect(markup).toContain('aria-hidden="true"');
  });

  it("labels each channel by name", () => {
    const markup = render(report);
    expect(markup).toContain("Instagram");
    expect(markup).toContain("Messenger");
  });

  it("says so when nothing started in the range", () => {
    const markup = render({
      ...report,
      started: 0,
      startedPerDay: report.startedPerDay.map((day) => ({ ...day, count: 0 })),
      medianFirstResponseSeconds: null,
    });
    expect(markup).toContain("No conversations started in this range.");
    expect(markup).toContain(">–<");
  });
});
