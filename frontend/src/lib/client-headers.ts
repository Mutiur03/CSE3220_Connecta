import { headers } from "next/headers";

/**
 * The browser's User-Agent and address, to pass on when this server signs a user in or refreshes
 * their session. The backend only sees this server's request, so without these the session list
 * would show the Next.js server instead of the person's device.
 */
export async function forwardedClientHeaders(): Promise<
  Record<string, string>
> {
  const incoming = await headers();
  const forwarded: Record<string, string> = {};
  const userAgent = incoming.get("user-agent");
  if (userAgent) forwarded["User-Agent"] = userAgent;
  const address = incoming.get("x-forwarded-for");
  if (address) forwarded["X-Forwarded-For"] = address;
  return forwarded;
}
