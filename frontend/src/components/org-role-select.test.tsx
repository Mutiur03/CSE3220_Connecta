import { renderToStaticMarkup } from "react-dom/server";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it, vi } from "vitest";

vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));
vi.mock("next/navigation", () => ({ redirect: vi.fn() }));
vi.mock("@/lib/session", () => ({ getAccessToken: vi.fn() }));

import OrgRoleSelect, { shownRole } from "./org-role-select";

const noop = async () => ({});

describe("shownRole", () => {
  it("shows the pick while a save runs, and the stored role otherwise", () => {
    expect(shownRole("ADMIN", "AGENT", true)).toBe("ADMIN");
    expect(shownRole("ADMIN", "AGENT", false)).toBe("AGENT");
  });

  it("falls back to the stored role, so a failed save does not leave an unsaved pick", () => {
    expect(shownRole("ADMIN", "AGENT", false)).toBe("AGENT");
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

  it("has no Save button: picking a role saves it", () => {
    expect(html).not.toContain("<button");
    expect(html).not.toContain("Save");
  });

  it("names the select after its row and shows the stored role", () => {
    expect(html).toContain('aria-label="Role for Ana"');
    expect(html).toMatch(/<option value="AGENT" selected/);
  });

  it("renders no alert before any action, and an empty status line for screen readers", () => {
    expect(html).not.toContain('role="alert"');
    expect(html).toMatch(/<span role="status" class="sr-only"><\/span>/);
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
