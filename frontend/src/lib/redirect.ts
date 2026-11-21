/**
 * Only same-site paths are allowed as a post-login target, never "//host" or "https://host".
 * Tabs and newlines are stripped by browsers before parsing, so "/\t/host" would become "//host".
 */
export function safeRedirectPath(next: string | null | undefined): string {
  if (!next || !next.startsWith("/") || next.startsWith("//")) return "/";
  if (/[\\\t\n\r]/.test(next)) return "/";
  return next;
}

/** `reason` value on /login when the backend could not be reached during a refresh. */
export const UNAVAILABLE_REASON = "unavailable";

/** Pages for signed-out visitors; a signed-in visitor on one of them is sent on. */
export const AUTH_PATHS = new Set(["/login", "/signup", "/setup", "/welcome"]);

/**
 * Where a signed-in visitor who opens /login goes: their safe `next`, never /login itself,
 * which would bounce them back to /login forever.
 */
export function signedInLoginTarget(next: string | null | undefined): string {
  const path = safeRedirectPath(next);
  const pathname = new URL(path, "http://localhost").pathname;
  return AUTH_PATHS.has(pathname.replace(/\/+$/, "")) ? "/" : path;
}
