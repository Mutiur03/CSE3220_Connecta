import { NextRequest, NextResponse } from "next/server";
import { ACCESS_COOKIE, REFRESH_COOKIE } from "@/lib/session-cookies";

// Optimistic check only: is there a session cookie at all? The backend validates the token itself
// (see getCurrentUser), so a forged cookie gets past this redirect but never past the API.
export function proxy(request: NextRequest) {
  const signedIn =
    request.cookies.has(ACCESS_COOKIE) || request.cookies.has(REFRESH_COOKIE);
  const onLogin = request.nextUrl.pathname === "/login";

  if (!signedIn && !onLogin) {
    const login = new URL("/login", request.url);
    const target = request.nextUrl.pathname + request.nextUrl.search;
    if (target !== "/") login.searchParams.set("next", target);
    return NextResponse.redirect(login);
  }
  if (signedIn && onLogin) {
    return NextResponse.redirect(new URL("/", request.url));
  }
  return NextResponse.next();
}

export const config = {
  // Not run for static assets or the refresh route, which does its own checks.
  matcher: ["/((?!_next/static|_next/image|favicon.ico|session/refresh).*)"],
};
