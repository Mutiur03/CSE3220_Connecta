import "server-only";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { ApiError, fetchMe, type Tokens, type User } from "./api";
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  accessCookieOptions,
  refreshCookieOptions,
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
 * the backend answers 401) goes through /session/refresh, which rotates the refresh token.
 */
export async function getCurrentUser(): Promise<User> {
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
  redirect(hasRefresh ? "/session/refresh?next=/" : "/login");
}
