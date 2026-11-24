import "server-only";

import { cookies, headers } from "next/headers";
import { redirect } from "next/navigation";
import { ApiError, fetchMe, type Tokens, type User } from "./api";
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  accessCookieOptions,
  refreshCookieOptions,
  REQUESTED_PATH_HEADER,
} from "./session-cookies";

const secure = () => process.env.NODE_ENV === "production";

export async function setSessionCookies(tokens: Tokens) {
  const store = await cookies();
  store.set(
    ACCESS_COOKIE,
    tokens.accessToken,
    accessCookieOptions(tokens.expiresIn, secure()),
  );
  store.set(
    REFRESH_COOKIE,
    tokens.refreshToken,
    refreshCookieOptions(secure()),
  );
}

export async function clearSessionCookies() {
  const store = await cookies();
  store.delete(ACCESS_COOKIE);
  store.delete(REFRESH_COOKIE);
}

export async function getRefreshToken() {
  return (await cookies()).get(REFRESH_COOKIE)?.value;
}

/**
 * The signed-in user, or a redirect. The proxy only checks that a cookie exists; this is the real
 * check, because the backend validates the token. An expired access token (its cookie is gone, or
 * the backend answers 401) goes through /session/refresh, which rotates the refresh token and then
 * returns to `next`.
 */
export async function getCurrentUser(next?: string): Promise<User> {
  next ??= await requestedPath();
  const store = await cookies();
  const accessToken = store.get(ACCESS_COOKIE)?.value;
  const hasRefresh = store.has(REFRESH_COOKIE);

  if (accessToken) {
    try {
      return await fetchMe(accessToken);
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 401)) throw error;
    }
  }
  redirect(hasRefresh ? refreshPath(next) : "/login");
}

/**
 * Where to return after a refresh when the caller gave no path (the sidebar panel renders on every
 * page). The proxy copies the requested path into this header; "/" only when it is absent.
 */
async function requestedPath() {
  return (await headers()).get(REQUESTED_PATH_HEADER) ?? "/";
}

/** `next` may carry its own query string, so it is encoded as one value. */
function refreshPath(next: string) {
  return `/session/refresh?next=${encodeURIComponent(next)}`;
}

/**
 * The access token for calls to the backend, or a redirect when it is gone. Pages and actions call
 * getCurrentUser first, which refreshes an expired token; this only reads the cookie.
 */
export async function getAccessToken(next?: string): Promise<string> {
  next ??= await requestedPath();
  const store = await cookies();
  const accessToken = store.get(ACCESS_COOKIE)?.value;
  if (accessToken) return accessToken;
  redirect(store.has(REFRESH_COOKIE) ? refreshPath(next) : "/login");
}
