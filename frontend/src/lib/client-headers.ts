import { headers } from "next/headers";

/**
 * The browser's User-Agent and address, to pass on when this server signs a user in or refreshes
 * their session. The backend only sees this server's request, so without these the session list
 * would show the Next.js server instead of the person's device.
 */
export function clientHeadersFrom(incoming: Headers): Record<string, string> {
  const forwarded: Record<string, string> = {};
  const userAgent = incoming.get("user-agent");
  if (userAgent) forwarded["User-Agent"] = userAgent;
  const address = incoming.get("x-forwarded-for");
  if (address) forwarded["X-Forwarded-For"] = address;
  return forwarded;
}

export async function forwardedClientHeaders(): Promise<
  Record<string, string>
> {
  return clientHeadersFrom(await headers());
}
