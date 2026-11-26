import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { MemberAvailabilityReport } from "@/lib/availability-report-api";
import AvailabilityTimeTable from "./availability-time-table";

const dates = ["2026-11-26", "2026-11-27"];
const member: MemberAvailabilityReport = {
  membershipId: "m1",
  userId: "u1",
  name: "Mira Das",
  role: "AGENT",
  totalSeconds: 8100,
  days: [
    { date: "2026-11-26", availableSeconds: 0 },
    { date: "2026-11-27", availableSeconds: 8100 },
  ],
};

const render = (members: MemberAvailabilityReport[]) =>
  renderToStaticMarkup(
    <AvailabilityTimeTable
      members={members}
      dates={dates}
      today="2026-11-27"
    />,
  );

describe("AvailabilityTimeTable", () => {
  it("shows the member, day headings, durations and the total", () => {
    const markup = render([member]);
    expect(markup).toContain("Mira Das");
    expect(markup).toContain("Agent");
    expect(markup).toContain("Thu 26");
    expect(markup).toContain("Fri 27");
    expect(markup).toContain(">–<");
    expect(markup).toContain("Total");
    expect(markup.match(/>2h 15m</g)).toHaveLength(2);
  });

  it("marks only today's column heading", () => {
    const markup = render([member]);
    expect(markup.match(/aria-current="date"/g)).toHaveLength(1);
    expect(markup).toMatch(/aria-current="date"[^>]*>Fri 27/);
  });

  it("names the table and scopes its headers", () => {
    const markup = render([member]);
    expect(markup).toContain("<caption");
    expect(markup).toContain('scope="row"');
    expect(markup).toContain('scope="col"');
  });

  it("says so when there are no members", () => {
    const markup = render([]);
    expect(markup).toContain("no members");
    expect(markup).not.toContain("<table");
  });
});
