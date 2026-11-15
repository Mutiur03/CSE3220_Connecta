import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import OrgRoleBadge, { ROLE_BADGE_TONE } from "./org-role-badge";
import { ORG_ROLES } from "@/lib/org-permissions";

describe("OrgRoleBadge", () => {
  it.each(ORG_ROLES)("renders the %s label", (role) => {
    expect(renderToStaticMarkup(<OrgRoleBadge role={role} />)).toContain(
      role.charAt(0) + role.slice(1).toLowerCase(),
    );
  });

  it("gives each role its own token tone", () => {
    const tones = ORG_ROLES.map((role) => ROLE_BADGE_TONE[role]);
    expect(new Set(tones).size).toBe(ORG_ROLES.length);
    for (const tone of tones) {
      expect(tone).not.toMatch(/zinc-|red-|dark:/);
    }
  });
});
