import { NextRequest, NextResponse } from "next/server";
import { ApiError, type Tokens } from "@/lib/api";
import { clientHeadersFrom } from "@/lib/client-headers";
import {
  isExpiringSoon,
  refreshOnce,
  setSessionCookiesOn,
} from "@/lib/refresh-session";
import {
  AUTH_PATHS,
  signedInLoginTarget,
  UNAVAILABLE_REASON,
} from "@/lib/redirect";
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  REQUESTED_PATH_HEADER,
} from "@/lib/session-cookies";

// Optimistic check only: is there a session cookie at all? The backend validates the token itself
// (see getCurrentUser), so a forged cookie gets past this redirect but never past the API.
// Open to signed-out visitors: an invited person has no account to sign in with yet.
const PUBLIC_PATHS = new Set(["/invitations/accept"]);

const REJECTED = "rejected";

export async function proxy(request: NextRequest) {
  const { pathname, searchParams } = request.nextUrl;
  const signedIn =
    request.cookies.has(ACCESS_COOKIE) || request.cookies.has(REFRESH_COOKIE);
  const onAuthPage = AUTH_PATHS.has(pathname);
  // The refresh route sends visitors here when the backend is down. Their cookies are kept, so
  // without this flag the bounce below would send them straight back into the same refresh loop.
  const backendDown = searchParams.get("reason") === UNAVAILABLE_REASON;

  // Signed-out visitors see the landing page at "/", under the same URL.
  if (!signedIn && pathname === "/") {
    return NextResponse.rewrite(new URL("/welcome", request.url));
  }
  if (!signedIn && !onAuthPage && !PUBLIC_PATHS.has(pathname)) {
    const login = new URL("/login", request.url);
    const target = pathname + request.nextUrl.search;
    if (target !== "/") login.searchParams.set("next", target);
    return NextResponse.redirect(login);
  }
  if (signedIn && onAuthPage && !backendDown) {
    const target = signedInLoginTarget(searchParams.get("next"));
    return NextResponse.redirect(new URL(target, request.url));
  }
  const refreshed = onAuthPage ? undefined : await refreshIfExpired(request);

  // Set (not appended) so a client cannot forge it; safeRedirectPath also guards the refresh route.
  const headers = new Headers(request.headers);
  headers.set(REQUESTED_PATH_HEADER, pathname + request.nextUrl.search);
  const response = NextResponse.next({ request: { headers } });
  if (refreshed === REJECTED) {
    // Also on the response: a dead cookie left in the browser would bounce /login back forever.
    response.cookies.delete(ACCESS_COOKIE);
    response.cookies.delete(REFRESH_COOKIE);
  } else if (refreshed) {
    setSessionCookiesOn(response, refreshed);
  }
  return response;
}

/**
 * Rotates the session before the page renders when the access cookie is gone or about to expire (see
 * isExpiringSoon) but the refresh cookie is still there, so the page never needs the /session/refresh
 * redirect. Concurrent requests share one backend call (refreshOnce). The new tokens go on
 * the request too, so this same render (and any server action) already sees them.
 * A rejected refresh token (401) ends the session; a concurrent rotation (409) or a backend outage
 * leaves the cookies alone, and getCurrentUser falls back to /session/refresh.
 */
async function refreshIfExpired(
  request: NextRequest,
): Promise<Tokens | typeof REJECTED | undefined> {
  const refreshToken = request.cookies.get(REFRESH_COOKIE)?.value;
  const accessToken = request.cookies.get(ACCESS_COOKIE)?.value;
  if (!refreshToken || (accessToken && !isExpiringSoon(accessToken))) {
    return undefined;
  }
  try {
    const tokens = await refreshOnce(
      refreshToken,
      clientHeadersFrom(request.headers),
    );
    request.cookies.set(ACCESS_COOKIE, tokens.accessToken);
    request.cookies.set(REFRESH_COOKIE, tokens.refreshToken);
    return tokens;
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      request.cookies.delete(REFRESH_COOKIE);
      return REJECTED;
    }
    return undefined;
  }
}

export const config = {
  // Not run for static assets or the refresh route, which does its own checks.
  matcher: [
    "/((?!_next/static|_next/image|favicon.ico|icon.png|apple-icon.png|brand/|session/refresh).*)",
  ],
};
