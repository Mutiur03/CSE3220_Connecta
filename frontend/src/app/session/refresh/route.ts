import { NextRequest } from "next/server";
import { ApiError, refresh } from "@/lib/api";
import { redirectTo } from "@/lib/redirect-response";
import { safeRedirectPath } from "@/lib/redirect";
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  accessCookieOptions,
  refreshCookieOptions,
} from "@/lib/session-cookies";

// Exchanges the refresh token for a new pair (the backend rotates it), then returns to `next`.
export async function GET(request: NextRequest) {
  const next = safeRedirectPath(request.nextUrl.searchParams.get("next"));
  const refreshToken = request.cookies.get(REFRESH_COOKIE)?.value;
  const toLogin = () => redirectTo("/login");

  if (!refreshToken) return toLogin();

  try {
    const tokens = await refresh(refreshToken);
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
    const response = toLogin();
    // Only a rejected token ends the session; a backend outage must not log people out.
    if (error instanceof ApiError && error.status === 401) {
      response.cookies.delete(ACCESS_COOKIE);
      response.cookies.delete(REFRESH_COOKIE);
    }
    return response;
  }
}
