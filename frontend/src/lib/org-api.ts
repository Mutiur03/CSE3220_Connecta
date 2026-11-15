import { request } from "./api";
import type { OrgRole } from "./org-permissions";

/** Default page size for the organization list. */
export const PAGE_SIZE = 20;
/** The backend's largest page. The default for member and invitation lists; the pages pass their own size. */
export const MAX_PAGE_SIZE = 100;

export type Page<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
};

export type Organization = {
  id: string;
  name: string;
  slug: string;
  /** The caller's own role in this organization. */
  callerRole: OrgRole;
  createdAt: string;
};

export type Member = {
  membershipId: string;
  userId: string;
  email: string;
  name: string;
  role: OrgRole;
  joinedAt: string;
};

export type InvitationStatus = "PENDING" | "ACCEPTED" | "REVOKED";

export type Invitation = {
  id: string;
  email: string;
  role: OrgRole;
  status: InvitationStatus;
  expiresAt: string;
  createdAt: string;
};

/** Creating an invitation returns the raw token once; the backend never returns it again. */
export type CreatedInvitation = { invitation: Invitation; token: string };

const orgPath = (orgId: string) =>
  `/api/organizations/${encodeURIComponent(orgId)}`;
const pageQuery = (page: number, size: number) => `?page=${page}&size=${size}`;

export const listOrganizations = (
  accessToken: string,
  page = 0,
  size = PAGE_SIZE,
) =>
  request<Page<Organization>>(`/api/organizations${pageQuery(page, size)}`, {
    accessToken,
  });

export const createOrganization = (
  accessToken: string,
  body: { name: string; slug: string },
) =>
  request<Organization>("/api/organizations", {
    method: "POST",
    body,
    accessToken,
  });

export const getOrganization = (accessToken: string, orgId: string) =>
  request<Organization>(orgPath(orgId), { accessToken });

export const renameOrganization = (
  accessToken: string,
  orgId: string,
  name: string,
) =>
  request<Organization>(orgPath(orgId), {
    method: "PATCH",
    body: { name },
    accessToken,
  });

export const deleteOrganization = (accessToken: string, orgId: string) =>
  request<void>(orgPath(orgId), { method: "DELETE", accessToken });

export const listMembers = (
  accessToken: string,
  orgId: string,
  page = 0,
  size = MAX_PAGE_SIZE,
) =>
  request<Page<Member>>(`${orgPath(orgId)}/members${pageQuery(page, size)}`, {
    accessToken,
  });

export const changeMemberRole = (
  accessToken: string,
  orgId: string,
  membershipId: string,
  role: OrgRole,
) =>
  request<Member>(
    `${orgPath(orgId)}/members/${encodeURIComponent(membershipId)}`,
    {
      method: "PATCH",
      body: { role },
      accessToken,
    },
  );

export const removeMember = (
  accessToken: string,
  orgId: string,
  membershipId: string,
) =>
  request<void>(
    `${orgPath(orgId)}/members/${encodeURIComponent(membershipId)}`,
    {
      method: "DELETE",
      accessToken,
    },
  );

export const listInvitations = (
  accessToken: string,
  orgId: string,
  page = 0,
  size = MAX_PAGE_SIZE,
) =>
  request<Page<Invitation>>(
    `${orgPath(orgId)}/invitations${pageQuery(page, size)}`,
    { accessToken },
  );

export const createInvitation = (
  accessToken: string,
  orgId: string,
  body: { email: string; role: OrgRole },
) =>
  request<CreatedInvitation>(`${orgPath(orgId)}/invitations`, {
    method: "POST",
    body,
    accessToken,
  });

export const revokeInvitation = (
  accessToken: string,
  orgId: string,
  invitationId: string,
) =>
  request<void>(
    `${orgPath(orgId)}/invitations/${encodeURIComponent(invitationId)}`,
    {
      method: "DELETE",
      accessToken,
    },
  );

export const acceptInvitation = (accessToken: string, token: string) =>
  request<Organization>("/api/invitations/accept", {
    method: "POST",
    body: { token },
    accessToken,
  });
