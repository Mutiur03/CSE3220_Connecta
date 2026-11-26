import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { MemberAvailability } from "@/lib/assignment-api";
import TeamWorkloadTable from "./team-workload-table";

const member = (over: Partial<MemberAvailability>): MemberAvailability => ({
  membershipId: "m1",
  userId: "u1",
  email: "mira@acme.test",
  name: "Mira Das",
  role: "AGENT",
  status: "AVAILABLE",
  updatedAt: "2026-11-26T09:00:00Z",
  openConversations: 4,
  ...over,
});

describe("TeamWorkloadTable", () => {
  it("shows name, role, availability, open count and last update", () => {
    const markup = renderToStaticMarkup(
      <TeamWorkloadTable
        members={[member({})]}
        now={new Date("2026-11-26T10:00:00Z")}
      />,
    );
    expect(markup).toContain("Mira Das");
    expect(markup).toContain("Agent");
    expect(markup).toContain("Available");
    expect(markup).toContain(">4<");
    expect(markup).toContain("1 hour ago");
  });

  it("shows Away and Never set for a member with no row", () => {
    const markup = renderToStaticMarkup(
      <TeamWorkloadTable
        members={[
          member({ status: "AWAY", updatedAt: null, openConversations: 0 }),
        ]}
        now={new Date()}
      />,
    );
    expect(markup).toContain("Away");
    expect(markup).toContain("Never set");
  });
});
