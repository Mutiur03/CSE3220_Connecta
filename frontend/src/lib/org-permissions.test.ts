import { describe, expect, it } from "vitest";
import {
  assignableRoles,
  canDelete,
  canInvite,
  canManageMember,
  canRemoveMember,
  canRename,
  isOrgRole,
} from "./org-permissions";

describe("org role rules", () => {
  it.each([
    ["OWNER", true, true, true],
    ["ADMIN", true, true, false],
    ["AGENT", false, false, false],
  ] as const)(
    "%s: rename %s, invite %s, delete %s",
    (role, rename, invite, del) => {
      expect(canRename(role)).toBe(rename);
      expect(canInvite(role)).toBe(invite);
      expect(canDelete(role)).toBe(del);
    },
  );

  it("lets a caller grant only roles at or below their own", () => {
    expect(assignableRoles("OWNER")).toEqual(["OWNER", "ADMIN", "AGENT"]);
    expect(assignableRoles("ADMIN")).toEqual(["ADMIN", "AGENT"]);
    expect(assignableRoles("AGENT")).toEqual([]);
  });

  it("lets an admin manage agents and admins but never an owner", () => {
    expect(canManageMember("ADMIN", "AGENT")).toBe(true);
    expect(canManageMember("ADMIN", "ADMIN")).toBe(true);
    expect(canManageMember("ADMIN", "OWNER")).toBe(false);
    expect(canManageMember("OWNER", "OWNER")).toBe(true);
  });

  it("never lets an agent manage anyone", () => {
    expect(canManageMember("AGENT", "AGENT")).toBe(false);
  });

  it("lets anyone remove themselves, but not others without rights", () => {
    expect(canRemoveMember("AGENT", "AGENT", true)).toBe(true);
    expect(canRemoveMember("AGENT", "AGENT", false)).toBe(false);
    expect(canRemoveMember("ADMIN", "OWNER", false)).toBe(false);
    expect(canRemoveMember("ADMIN", "AGENT", false)).toBe(true);
  });

  it("recognises only the three role names", () => {
    expect(isOrgRole("ADMIN")).toBe(true);
    expect(isOrgRole("admin")).toBe(false);
    expect(isOrgRole("SUPERUSER")).toBe(false);
  });
});
