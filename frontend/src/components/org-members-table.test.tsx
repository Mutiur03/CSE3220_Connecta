import { renderToStaticMarkup } from "react-dom/server";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it, vi } from "vitest";
import type { Member } from "@/lib/org-api";

vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));
vi.mock("next/navigation", () => ({ redirect: vi.fn() }));
vi.mock("@/lib/session", () => ({ getAccessToken: vi.fn() }));

import OrgMembersTable, {
  memberInitials,
  rowRights,
  selfFirst,
} from "./org-members-table";

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
    expect(rowRights("ADMIN", "me", member("me", "ADMIN"))).toEqual({
      isSelf: true,
      canChangeRole: false,
      canRemove: true,
    });
  });

  it("lets an admin change and remove agents and other admins", () => {
    const agent = rowRights("ADMIN", "me", member("a", "AGENT"));
    expect(agent).toMatchObject({ canChangeRole: true, canRemove: true });
    const admin = rowRights("ADMIN", "me", member("o", "ADMIN"));
    expect(admin).toMatchObject({ canChangeRole: true, canRemove: true });
  });

  it("gives an agent no controls on other rows", () => {
    expect(rowRights("AGENT", "me", member("a", "AGENT"))).toMatchObject({
      canChangeRole: false,
      canRemove: false,
    });
  });
});

describe("selfFirst", () => {
  it("moves your own row to the top and keeps the rest in server order", () => {
    const rows = ["a", "me", "b", "c"].map((id) => member(id, "AGENT"));

    expect(selfFirst(rows, "me").map((row) => row.userId)).toEqual([
      "me",
      "a",
      "b",
      "c",
    ]);
  });

  it("leaves the order alone when you are not on this page", () => {
    const rows = ["a", "b"].map((id) => member(id, "AGENT"));

    expect(selfFirst(rows, "me").map((row) => row.userId)).toEqual(["a", "b"]);
  });
});

describe("memberInitials", () => {
  it("takes up to two initials from the name", () => {
    expect(memberInitials("ana maria lopez", "a@x.io")).toBe("AM");
    expect(memberInitials("  Mina ", "m@x.io")).toBe("M");
  });

  it("falls back to the email when the name is blank", () => {
    expect(memberInitials("   ", "zed@x.io")).toBe("Z");
  });
});

describe("OrgMembersTable markup", () => {
  const members = [
    member("me", "ADMIN", "Mina"),
    member("ana", "AGENT", "Ana"),
  ];
  const html = (role: "ADMIN" | "AGENT") =>
    renderToStaticMarkup(
      <OrgMembersTable
        orgId="org-1"
        members={members}
        callerRole={role}
        callerUserId="me"
      />,
    );

  it("renders exactly one select, for the changeable row only", () => {
    // One list serves every width, so each control exists once in the DOM.
    const markup = html("ADMIN");
    expect(markup.match(/<select/g)).toHaveLength(1);
    expect(markup).toContain('aria-label="Role for Ana"');
    expect(markup).not.toContain('aria-label="Role for Mina"');
    expect(markup).not.toContain('aria-label="Remove Mina"');
  });

  it("renders no selects and no remove control on other rows for an agent", () => {
    const markup = html("AGENT");
    expect(markup).not.toContain("<select");
    expect(markup).not.toContain('aria-label="Remove');
    // An agent may still leave: its own row keeps the Leave action.
    expect(markup).toContain('aria-label="Leave organization"');
  });

  it("renders one labelled list with a row per member and marks your own row", () => {
    const markup = html("ADMIN");
    expect(markup).toContain('<ul aria-label="Members"');
    expect(markup.match(/<li/g)).toHaveLength(members.length);
    expect(markup).toContain(">You<");
  });

  it("keeps zinc and dark pairs out of the members table source", () => {
    const source = readFileSync(
      fileURLToPath(new URL("./org-members-table.tsx", import.meta.url)),
      "utf8",
    );
    expect(source).not.toMatch(/zinc-|red-|dark:/);
  });
});
