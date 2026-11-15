import { renderToStaticMarkup } from "react-dom/server";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it, vi } from "vitest";
import type { Member } from "@/lib/org-api";

vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));
vi.mock("next/navigation", () => ({ redirect: vi.fn() }));
vi.mock("@/lib/session", () => ({ getAccessToken: vi.fn() }));

import OrgMembersTable, { rowRights } from "./org-members-table";

const member = (userId: string, role: Member["role"], name = userId): Member =>
  ({
    membershipId: `m-${userId}`,
    userId,
    name,
    email: `${userId}@x.io`,
    role,
    joinedAt: "2026-01-02T00:00:00Z",
  }) as Member;

describe("rowRights", () => {
  it("never offers a role change on your own row, but offers Leave", () => {
    expect(rowRights("OWNER", "me", member("me", "OWNER"))).toEqual({
      isSelf: true,
      canChangeRole: false,
      canRemove: true,
    });
  });

  it("lets an admin change and remove agents, not owners", () => {
    const agent = rowRights("ADMIN", "me", member("a", "AGENT"));
    expect(agent).toMatchObject({ canChangeRole: true, canRemove: true });
    const owner = rowRights("ADMIN", "me", member("o", "OWNER"));
    expect(owner).toMatchObject({ canChangeRole: false, canRemove: false });
  });

  it("gives an agent no controls on other rows", () => {
    expect(rowRights("AGENT", "me", member("a", "AGENT"))).toMatchObject({
      canChangeRole: false,
      canRemove: false,
    });
  });
});

describe("OrgMembersTable markup", () => {
  const members = [
    member("me", "OWNER", "Mina"),
    member("ana", "AGENT", "Ana"),
  ];
  const html = (role: "OWNER" | "AGENT") =>
    renderToStaticMarkup(
      <OrgMembersTable
        orgId="org-1"
        members={members}
        callerRole={role}
        callerUserId="me"
      />,
    );

  it("renders one select per layout for the changeable row (card list and table)", () => {
    // CSS (md:hidden / hidden md:block) shows one copy; the other is out of the a11y tree.
    const markup = html("OWNER");
    expect(markup.match(/<select/g)).toHaveLength(2);
    expect(markup).not.toContain('aria-label="Remove Mina"');
  });

  it("renders no selects and no remove control on other rows for an agent", () => {
    const markup = html("AGENT");
    expect(markup).not.toContain("<select");
    expect(markup).not.toContain('aria-label="Remove');
    // An agent may still leave: its own row keeps the Leave action.
    expect(markup).toContain('aria-label="Leave organization"');
  });

  it("shows the card list below md and the table from md up", () => {
    const markup = html("OWNER");
    expect(markup).toContain("md:hidden");
    expect(markup).toContain("hidden md:block");
    expect(markup).toContain('aria-label="Members"');
  });

  it("keeps zinc and dark pairs out of the members table source", () => {
    const source = readFileSync(
      fileURLToPath(new URL("./org-members-table.tsx", import.meta.url)),
      "utf8",
    );
    expect(source).not.toMatch(/zinc-|red-|dark:/);
  });
});
