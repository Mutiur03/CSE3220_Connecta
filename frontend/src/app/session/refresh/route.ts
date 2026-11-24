import { NextRequest } from "next/server";
import { ApiError } from "@/lib/api";
import { forwardedClientHeaders } from "@/lib/client-headers";
import { refreshOnce, setSessionCookiesOn } from "@/lib/refresh-session";
import { redirectTo } from "@/lib/redirect-response";
import { safeRedirectPath, UNAVAILABLE_REASON } from "@/lib/redirect";
import { ACCESS_COOKIE, REFRESH_COOKIE } from "@/lib/session-cookies";

// Exchanges the refresh token for a new pair (the backend rotates it), then returns to `next`.
// GET is a deliberate exception to the REST rule: a server component can only redirect, and
// cookies can only be set from a route handler, so the rotation has to happen on this redirect.
export async function GET(request: NextRequest) {
  const next = safeRedirectPath(request.nextUrl.searchParams.get("next"));
  const refreshToken = request.cookies.get(REFRESH_COOKIE)?.value;

  if (!refreshToken) return redirectTo("/login");

  try {
    const tokens = await refreshOnce(
      refreshToken,
      await forwardedClientHeaders(),
    );
    const response = redirectTo(next);
    setSessionCookiesOn(response, tokens);
    return response;
  } catch (error) {
    // 409: another request (other tab, other instance) rotated this token a moment ago and its
    // response carries the new cookies. Keep ours and go on; the session is still valid.
    if (error instanceof ApiError && error.status === 409)
      return redirectTo(next);
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
