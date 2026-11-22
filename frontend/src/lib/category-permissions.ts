import type { OrgRole } from "./org-permissions";

// Mirrors the backend: creating, editing and deleting categories needs ADMIN of the organization.
// Any member may read them. The UI uses this only to show or hide controls.
export const canManageCategories = (caller: OrgRole) => caller === "ADMIN";
