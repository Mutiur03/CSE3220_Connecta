// Mirrors the backend role rules (OWNER > ADMIN > AGENT). The UI uses these only to show or hide
// controls; the backend still enforces every rule.

export const ORG_ROLES = ["OWNER", "ADMIN", "AGENT"] as const;
export type OrgRole = (typeof ORG_ROLES)[number];

const RANK: Record<OrgRole, number> = { AGENT: 0, ADMIN: 1, OWNER: 2 };

export const ROLE_LABELS: Record<OrgRole, string> = {
  OWNER: "Owner",
  ADMIN: "Admin",
  AGENT: "Agent",
};

export function isOrgRole(value: string): value is OrgRole {
  return (ORG_ROLES as readonly string[]).includes(value);
}

const isAdminOrAbove = (role: OrgRole) => RANK[role] >= RANK.ADMIN;

export const canRename = (caller: OrgRole) => isAdminOrAbove(caller);
export const canInvite = (caller: OrgRole) => isAdminOrAbove(caller);
export const canDelete = (caller: OrgRole) => caller === "OWNER";

/** Roles the caller may grant: never above their own. Empty for an AGENT. */
export function assignableRoles(caller: OrgRole): OrgRole[] {
  if (!isAdminOrAbove(caller)) return [];
  return ORG_ROLES.filter((role) => RANK[role] <= RANK[caller]);
}

/** An ADMIN cannot change or remove an OWNER; nobody above their own role can be touched. */
export function canManageMember(caller: OrgRole, target: OrgRole): boolean {
  return isAdminOrAbove(caller) && RANK[target] <= RANK[caller];
}

/** Anyone may leave; removing someone else needs canManageMember. */
export function canRemoveMember(
  caller: OrgRole,
  target: OrgRole,
  isSelf: boolean,
): boolean {
  return isSelf || canManageMember(caller, target);
}
