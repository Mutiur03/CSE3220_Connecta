export const ACCESS_COOKIE = "access_token";
export const REFRESH_COOKIE = "refresh_token";

// Matches the backend's default refresh token lifetime (app.jwt.refresh-ttl=7d).
const REFRESH_MAX_AGE_SECONDS = 7 * 24 * 60 * 60;

type CookieOptions = {
  httpOnly: true;
  secure: boolean;
  sameSite: "lax";
  path: "/";
  maxAge: number;
};

// httpOnly keeps both tokens out of reach of page scripts (XSS), sameSite=lax blocks cross-site posts.
function options(maxAge: number, secure: boolean): CookieOptions {
  return { httpOnly: true, secure, sameSite: "lax", path: "/", maxAge };
}

/** The access cookie expires together with the token, so a missing cookie means "refresh needed". */
export const accessCookieOptions = (expiresIn: number, secure: boolean) =>
  options(expiresIn, secure);

export const refreshCookieOptions = (secure: boolean) =>
  options(REFRESH_MAX_AGE_SECONDS, secure);
