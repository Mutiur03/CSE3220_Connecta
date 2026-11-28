import { afterEach, describe, expect, it, vi } from "vitest";
import {
  getAgentAnalytics,
  getConversationAnalytics,
  type AgentAnalyticsPage,
  type ConversationAnalytics,
} from "./analytics-api";
import { ApiError } from "./api";

const ORG = "11111111-2222-3333-4444-555555555555";
const range = { from: "2026-11-01", to: "2026-11-28", zone: "Asia/Dhaka" };

// The examples from docs/analytics-metrics.md, typed, so a drift from the contract fails to compile.
const conversations: ConversationAnalytics = {
  ...range,
  started: 142,
  startedPerDay: [{ date: "2026-11-01", count: 5 }],
  startedPerChannel: [
    { channel: "INSTAGRAM", count: 90 },
    { channel: "MESSENGER", count: 52 },
  ],
  statusNow: { open: 37, closed: 410 },
  messagesReceived: 1210,
  messagesSent: 980,
  medianFirstResponseSeconds: 540,
  unanswered: 8,
};

const agents: AgentAnalyticsPage = {
  ...range,
  content: [
    {
      membershipId: "6b1c0000-0000-0000-0000-000000000000",
      name: "Aavas Roy",
      role: "AGENT",
      assigned: 48,
      resolved: 41,
      openNow: 6,
      repliesSent: 312,
      availableSeconds: 302400,
      medianFirstResponseSeconds: 420,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 4,
};

function serve(body: unknown, status = 200) {
  const fetchMock = vi.fn(
    async () =>
      new Response(JSON.stringify(body), {
        status,
        headers: { "Content-Type": "application/json" },
      }),
  );
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

const calledUrl = (fetchMock: ReturnType<typeof serve>) =>
  new URL(String((fetchMock.mock.calls[0] as unknown[])[0]));

afterEach(() => vi.unstubAllGlobals());

describe("getConversationAnalytics", () => {
  it("asks for the range in the reader's zone and returns the report", async () => {
    const fetchMock = serve(conversations);

    const report = await getConversationAnalytics("tok", ORG, range);

    const url = calledUrl(fetchMock);
    expect(url.pathname).toBe(
      `/api/organizations/${ORG}/analytics/conversations`,
    );
    expect(Object.fromEntries(url.searchParams)).toEqual(range);
    expect(report).toEqual(conversations);
  });

  it("raises an ApiError when the backend refuses", async () => {
    serve({ status: 404, title: "Not Found" }, 404);

    await expect(
      getConversationAnalytics("tok", ORG, range),
    ).rejects.toBeInstanceOf(ApiError);
  });
});

describe("getAgentAnalytics", () => {
  it("sends the range with the page and size", async () => {
    const fetchMock = serve(agents);

    const page = await getAgentAnalytics("tok", ORG, range, 2, 20);

    const url = calledUrl(fetchMock);
    expect(url.pathname).toBe(`/api/organizations/${ORG}/analytics/agents`);
    expect(Object.fromEntries(url.searchParams)).toEqual({
      ...range,
      page: "2",
      size: "20",
    });
    expect(page.content[0].name).toBe("Aavas Roy");
  });
});
