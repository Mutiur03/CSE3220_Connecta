import { describe, expect, it } from "vitest";
import {
  assignableRoles,
  canDelete,
  canInvite,
  canManageMember,
  canRemoveMember,
  canRename,
  isOrgRole,
  isSystemOwner,
  roleLabel,
} from "./org-permissions";

describe("org role rules", () => {
  it.each([
    ["ADMIN", true, true, true],
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
    expect(assignableRoles("ADMIN")).toEqual(["ADMIN", "AGENT"]);
    expect(assignableRoles("AGENT")).toEqual([]);
  });

  it("lets an admin manage agents and other admins", () => {
    expect(canManageMember("ADMIN", "AGENT")).toBe(true);
    expect(canManageMember("ADMIN", "ADMIN")).toBe(true);
  });

  it("never lets an agent manage anyone", () => {
    expect(canManageMember("AGENT", "AGENT")).toBe(false);
  });

  it("lets anyone remove themselves, but not others without rights", () => {
    expect(canRemoveMember("AGENT", "AGENT", true)).toBe(true);
    expect(canRemoveMember("AGENT", "AGENT", false)).toBe(false);
    expect(canRemoveMember("ADMIN", "ADMIN", false)).toBe(true);
    expect(canRemoveMember("ADMIN", "AGENT", false)).toBe(true);
  });

  it("labels the system owner role, but it is not an organization role", () => {
    expect(roleLabel("OWNER")).toBe("Owner");
    expect(roleLabel("ADMIN")).toBe("Admin");
    expect(roleLabel("SOMETHING")).toBe("SOMETHING");
    expect(isOrgRole("OWNER")).toBe(false);
    expect(isSystemOwner(["OWNER"])).toBe(true);
    expect(isSystemOwner(["ADMIN"])).toBe(false);
  });

  it("recognises only the organization role names", () => {
    expect(isOrgRole("ADMIN")).toBe(true);
    expect(isOrgRole("admin")).toBe(false);
    expect(isOrgRole("SUPERUSER")).toBe(false);
  });
});
