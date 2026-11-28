import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { AgentAnalytics } from "@/lib/analytics-api";
import AgentAnalyticsTable from "./agent-analytics-table";

const agent: AgentAnalytics = {
  membershipId: "m1",
  name: "Aavas Roy",
  role: "AGENT",
  assigned: 48,
  resolved: 41,
  openNow: 6,
  repliesSent: 312,
  availableSeconds: 8100,
  medianFirstResponseSeconds: null,
};

describe("AgentAnalyticsTable", () => {
  it("shows each member with their role and readable durations", () => {
    const markup = renderToStaticMarkup(
      <AgentAnalyticsTable agents={[agent]} />,
    );
    expect(markup).toContain('aria-label="Agent performance"');
    expect(markup).toContain("Aavas Roy");
    expect(markup).toContain("Agent");
    expect(markup).toContain(">312<");
    expect(markup).toContain(">2h 15m<");
    expect(markup).toContain(">–<");
  });
});
