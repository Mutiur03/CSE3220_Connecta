import type { NextResponse } from "next/server";
import { refresh, type Tokens } from "@/lib/api";
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  accessCookieOptions,
  refreshCookieOptions,
} from "@/lib/session-cookies";

// Refresh tokens are single-use. After an expiry a navigation fires several requests at once (page,
// layout, prefetches), each carrying the same cookie: the first rotates it, the rest would look like a
// replay and sign the user out everywhere. So concurrent calls share one backend call for a few seconds.
// ponytail: per-process memory, so it assumes one frontend instance; the backend's grace window covers the rest.
const SHARE_MS = 10_000;
const inFlight = new Map<string, Promise<Tokens>>();

export function refreshOnce(
  token: string,
  clientHeaders: Record<string, string>,
): Promise<Tokens> {
  let call = inFlight.get(token);
  if (!call) {
    call = refresh(token, clientHeaders);
    inFlight.set(token, call);
    const forget = () => setTimeout(() => inFlight.delete(token), SHARE_MS);
    call.then(forget, () => inFlight.delete(token));
  }
  return call;
}

/** Puts a rotated token pair on a response as the two httpOnly session cookies. */
export function setSessionCookiesOn(response: NextResponse, tokens: Tokens) {
  const secure = process.env.NODE_ENV === "production";
  response.cookies.set(
    ACCESS_COOKIE,
    tokens.accessToken,
    accessCookieOptions(tokens.expiresIn, secure),
  );
  response.cookies.set(
    REFRESH_COOKIE,
    tokens.refreshToken,
    refreshCookieOptions(secure),
  );
}
