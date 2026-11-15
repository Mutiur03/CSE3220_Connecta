import { renderToStaticMarkup } from "react-dom/server";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it, vi } from "vitest";

vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));
vi.mock("next/navigation", () => ({ redirect: vi.fn() }));
vi.mock("@/lib/session", () => ({ getAccessToken: vi.fn() }));

import OrgRoleSelect, { canSaveRole } from "./org-role-select";

const noop = async () => ({});

describe("canSaveRole", () => {
  it("offers a save only for a real change while idle", () => {
    expect(canSaveRole("ADMIN", "AGENT", false)).toBe(true);
    expect(canSaveRole("AGENT", "AGENT", false)).toBe(false);
    expect(canSaveRole("ADMIN", "AGENT", true)).toBe(false);
  });
});

describe("OrgRoleSelect markup", () => {
  const html = renderToStaticMarkup(
    <OrgRoleSelect
      action={noop}
      currentRole="AGENT"
      options={["ADMIN", "AGENT"]}
      label="Role for Ana"
    />,
  );

  it("starts with an unchanged role, so Save is disabled", () => {
    expect(html).toMatch(
      /<button[^>]*disabled[^>]*aria-label="Save role for Ana"/,
    );
  });

  it("names the save button after its row", () => {
    expect(html).toContain('aria-label="Save role for Ana"');
  });

  it("renders no alert or status before any action", () => {
    expect(html).not.toContain('role="alert"');
    expect(html).not.toContain('role="status"');
  });

  it("uses token classes, not zinc or dark pairs", () => {
    const source = readFileSync(
      fileURLToPath(new URL("./org-role-select.tsx", import.meta.url)),
      "utf8",
    );
    expect(source).not.toMatch(/zinc-|red-|dark:/);
    expect(html).not.toMatch(/zinc-|red-|dark:/);
  });
});
