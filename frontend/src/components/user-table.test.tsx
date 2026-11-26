import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import type { AdminUser } from "@/lib/user-api";

vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));
vi.mock("next/navigation", () => ({ redirect: vi.fn() }));
vi.mock("@/lib/session", () => ({
  getAccessToken: vi.fn(),
  getCurrentUser: vi.fn(),
}));

import UserTable from "./user-table";

const user = (id: string, over: Partial<AdminUser> = {}): AdminUser => ({
  id,
  email: `${id}@x.io`,
  fullName: `Name ${id}`,
  enabled: true,
  roles: ["ADMIN"],
  ...over,
});

const render = (users: AdminUser[]) =>
  renderToStaticMarkup(<UserTable users={users} callerUserId="me" />);

describe("UserTable", () => {
  it("shows an active user as Active with a confirmed Disable", () => {
    const html = render([user("a")]);
    expect(html).toContain("Active");
    expect(html).toContain('aria-label="Disable Name a"');
    expect(html).toContain("<dialog");
  });

  it("shows a disabled user as Disabled with Enable and no dialog", () => {
    const html = render([user("a", { enabled: false })]);
    expect(html).toContain("Disabled");
    expect(html).toContain('aria-label="Enable Name a"');
    expect(html).not.toContain("<dialog");
  });

  it("gives the owner's own row a You hint and no action", () => {
    const html = render([user("me", { roles: ["OWNER"] })]);
    expect(html).toContain("You");
    expect(html).not.toContain("<button");
  });

  it("gives another owner an Owner hint and no action", () => {
    const html = render([user("o", { roles: ["OWNER"] })]);
    expect(html).not.toContain("<button");
    expect(html).toContain(">Owner</span></div>");
  });
});
