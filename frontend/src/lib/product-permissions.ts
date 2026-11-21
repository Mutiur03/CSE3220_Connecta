import type { OrgRole } from "./org-permissions";

// Mirrors the backend: creating, editing and deleting products needs ADMIN of the organization.
// Any member may read the catalog. The UI uses this only to show or hide controls.
export const canManageProducts = (caller: OrgRole) => caller === "ADMIN";
