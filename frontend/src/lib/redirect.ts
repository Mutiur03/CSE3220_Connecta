/**
 * Only same-site paths are allowed as a post-login target, never "//host" or "https://host".
 * Tabs and newlines are stripped by browsers before parsing, so "/\t/host" would become "//host".
 */
export function safeRedirectPath(next: string | null | undefined): string {
  if (!next || !next.startsWith("/") || next.startsWith("//")) return "/";
  if (/[\\\t\n\r]/.test(next)) return "/";
  return next;
}
