import { NextRequest } from "next/server";
import { ApiError, refresh } from "@/lib/api";
import { forwardedClientHeaders } from "@/lib/client-headers";
import { redirectTo } from "@/lib/redirect-response";
import { safeRedirectPath, UNAVAILABLE_REASON } from "@/lib/redirect";
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  accessCookieOptions,
  refreshCookieOptions,
} from "@/lib/session-cookies";

// Exchanges the refresh token for a new pair (the backend rotates it), then returns to `next`.
// GET is a deliberate exception to the REST rule: a server component can only redirect, and
// cookies can only be set from a route handler, so the rotation has to happen on this redirect.
export async function GET(request: NextRequest) {
  const next = safeRedirectPath(request.nextUrl.searchParams.get("next"));
  const refreshToken = request.cookies.get(REFRESH_COOKIE)?.value;

  if (!refreshToken) return redirectTo("/login");

  try {
    const tokens = await refresh(refreshToken, await forwardedClientHeaders());
    const secure = process.env.NODE_ENV === "production";
    const response = redirectTo(next);
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
    return response;
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      // Only a rejected token ends the session.
      const response = redirectTo("/login");
      response.cookies.delete(ACCESS_COOKIE);
      response.cookies.delete(REFRESH_COOKIE);
      return response;
    }
    // A backend outage must not log people out: the cookies stay, and /login says what happened.
    return redirectTo(`/login?reason=${UNAVAILABLE_REASON}`);
  }
}
