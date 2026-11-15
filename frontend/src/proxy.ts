import { NextRequest, NextResponse } from "next/server";
import { signedInLoginTarget, UNAVAILABLE_REASON } from "@/lib/redirect";
import { ACCESS_COOKIE, REFRESH_COOKIE } from "@/lib/session-cookies";

// Optimistic check only: is there a session cookie at all? The backend validates the token itself
// (see getCurrentUser), so a forged cookie gets past this redirect but never past the API.
export function proxy(request: NextRequest) {
  const { pathname, searchParams } = request.nextUrl;
  const signedIn =
    request.cookies.has(ACCESS_COOKIE) || request.cookies.has(REFRESH_COOKIE);
  const onLogin = pathname === "/login";
  // The refresh route sends visitors here when the backend is down. Their cookies are kept, so
  // without this flag the bounce below would send them straight back into the same refresh loop.
  const backendDown = searchParams.get("reason") === UNAVAILABLE_REASON;

  if (!signedIn && !onLogin) {
    const login = new URL("/login", request.url);
    const target = pathname + request.nextUrl.search;
    if (target !== "/") login.searchParams.set("next", target);
    return NextResponse.redirect(login);
  }
  if (signedIn && onLogin && !backendDown) {
    const target = signedInLoginTarget(searchParams.get("next"));
    return NextResponse.redirect(new URL(target, request.url));
  }
  return NextResponse.next();
}

export const config = {
  // Not run for static assets or the refresh route, which does its own checks.
  matcher: ["/((?!_next/static|_next/image|favicon.ico|session/refresh).*)"],
};
