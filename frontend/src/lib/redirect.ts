/** Only same-site paths are allowed as a post-login target, never "//host" or "https://host". */
export function safeRedirectPath(next: string | null | undefined): string {
  if (!next || !next.startsWith("/") || next.startsWith("//")) return "/";
  if (next.includes("\\")) return "/";
  return next;
}
