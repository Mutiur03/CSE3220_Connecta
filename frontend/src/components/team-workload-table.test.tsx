import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import type { MemberAvailability } from "@/lib/assignment-api";
import TeamWorkloadTable from "./team-workload-table";

vi.mock("@/lib/assignment-actions", () => ({
  setAssignableAction: { bind: () => async () => ({}) },
}));

const member = (over: Partial<MemberAvailability>): MemberAvailability => ({
  membershipId: "m1",
  userId: "u1",
  email: "mira@acme.test",
  name: "Mira Das",
  role: "AGENT",
  assignable: true,
  online: true,
  openConversations: 4,
  ...over,
});

const render = (members: MemberAvailability[]) =>
  renderToStaticMarkup(<TeamWorkloadTable orgId="org" members={members} />);

describe("TeamWorkloadTable", () => {
  it("shows name, role, online status, a checked switch and the open count", () => {
    const markup = render([member({})]);
    expect(markup).toContain("Mira Das");
    expect(markup).toContain("Agent");
    expect(markup).toContain("Online");
    expect(markup).toContain('role="switch"');
    expect(markup).toMatch(/<input[^>]*checked/);
    expect(markup).toContain(">4<");
    expect(markup).not.toContain("open, offline");
  });

  it("shows Offline, an unchecked switch and no flag when nothing is open", () => {
    const markup = render([
      member({ online: false, assignable: false, openConversations: 0 }),
    ]);
    expect(markup).toContain("Offline");
    expect(markup).not.toMatch(/<input[^>]*checked/);
    expect(markup).not.toContain("open, offline");
  });

  it("flags an offline member who holds open conversations", () => {
    expect(render([member({ online: false })])).toContain("open, offline");
  });

  it("renders a member the backend sent without the new fields as offline and not assignable", () => {
    const legacy = { ...member({}), online: undefined, assignable: undefined };
    const markup = render([legacy as unknown as MemberAvailability]);
    expect(markup).toContain("Offline");
    expect(markup).not.toMatch(/<input[^>]*checked/);
  });
});
