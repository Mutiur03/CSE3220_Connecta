// Mirrors the backend role rules. Inside an organization a member is an ADMIN or an AGENT. OWNER
// is the account-level role of the system owner (the SaaS owner created at /setup): it belongs to
// no organization. The UI uses these only to show or hide controls; the backend enforces every rule.

export const ORG_ROLES = ["ADMIN", "AGENT"] as const;
export type OrgRole = (typeof ORG_ROLES)[number];

/** The account-level role of the system owner. */
export const OWNER_ROLE = "OWNER";

const RANK: Record<OrgRole, number> = { AGENT: 0, ADMIN: 1 };

export const ROLE_LABELS: Record<OrgRole | typeof OWNER_ROLE, string> = {
  OWNER: "Owner",
  ADMIN: "Admin",
  AGENT: "Agent",
};

/** Display name for an organization or account role; unknown values show as sent. */
export function roleLabel(role: string): string {
  return role in ROLE_LABELS
    ? ROLE_LABELS[role as keyof typeof ROLE_LABELS]
    : role;
}

export const isSystemOwner = (roles: readonly string[]) =>
  roles.includes(OWNER_ROLE);

export function isOrgRole(value: string): value is OrgRole {
  return (ORG_ROLES as readonly string[]).includes(value);
}

const isAdminOrAbove = (role: OrgRole) => RANK[role] >= RANK.ADMIN;

export const canRename = (caller: OrgRole) => isAdminOrAbove(caller);
export const canInvite = (caller: OrgRole) => isAdminOrAbove(caller);
export const canDelete = (caller: OrgRole) => isAdminOrAbove(caller);

/** Roles the caller may grant: never above their own. Empty for an AGENT. */
export function assignableRoles(caller: OrgRole): OrgRole[] {
  if (!isAdminOrAbove(caller)) return [];
  return ORG_ROLES.filter((role) => RANK[role] <= RANK[caller]);
}

/** Nobody above the caller's own role can be touched: an AGENT manages no one. */
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
