/** The cookie that remembers the organization the person last worked in. */
export const CURRENT_ORG_COOKIE = "connecta_org";

const ORG_ROUTE =
  /^\/organizations\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})(?:\/|$)/i;

/** The organization id in an /organizations/<uuid>/... path, or undefined outside one. */
export function orgIdFromPath(pathname: string): string | undefined {
  return ORG_ROUTE.exec(pathname)?.[1];
}

/**
 * The organization the person is working in: the one in the address, else the one remembered in
 * the cookie, else the first. Undefined when they belong to none.
 */
export function selectCurrentOrganization<T extends { id: string }>(
  organizations: T[],
  pathname: string,
  remembered?: string,
): T | undefined {
  const fromPath = orgIdFromPath(pathname);
  return (
    organizations.find((org) => org.id === fromPath) ??
    organizations.find((org) => org.id === remembered) ??
    organizations[0]
  );
}
