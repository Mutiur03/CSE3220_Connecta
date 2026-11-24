import { cache } from "react";
import { notFound } from "next/navigation";
import { ApiError } from "@/lib/api";
import { getOrganization } from "@/lib/org-api";
import { isUuid } from "@/lib/org-validation";
import { getAccessToken, getCurrentUser } from "@/lib/session";

// Each section of the organization page calls these. React's cache() runs each one once per
// request, so the sections share a single /api/me and organization request.

const isNotFound = (error: unknown) =>
  error instanceof ApiError && error.status === 404;

/** The signed-in user and their access token. Redirects to log in when there is none. */
// orgId is only the cache key: it keeps one session lookup per organization per request.
// eslint-disable-next-line @typescript-eslint/no-unused-vars
export const loadSession = cache(async (orgId: string) => {
  // No path: every section of the organization (products, categories, ...) shares this, so after a
  // refresh the user must return to the page they asked for, which the proxy header carries.
  const user = await getCurrentUser();
  const accessToken = await getAccessToken();
  return { user, accessToken };
});

/**
 * The organization, or undefined when the id is malformed, missing or belongs to another tenant. The
 * backend answers 404 for the last two, so they look the same to the caller.
 */
export const findOrganization = cache(async (orgId: string) => {
  if (!isUuid(orgId)) return undefined;
  const { accessToken } = await loadSession(orgId);
  return getOrganization(accessToken, orgId).catch((error: unknown) => {
    if (isNotFound(error)) return undefined;
    throw error;
  });
});

/** The organization, or the not-found page. */
export const loadOrganization = cache(async (orgId: string) => {
  const organization = await findOrganization(orgId);
  if (!organization) notFound();
  return organization;
});

/** A backend 404 on a section's own request shows the not-found page, the same as a missing organization. */
export function notFoundOnMissing<T>(request: Promise<T>): Promise<T> {
  return request.catch((error: unknown) => {
    if (isNotFound(error)) notFound();
    throw error;
  });
}
