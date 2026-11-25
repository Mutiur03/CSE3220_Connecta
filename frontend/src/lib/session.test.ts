import { beforeEach, describe, expect, it, vi } from "vitest";

const store = vi.hoisted(() => ({
  get: vi.fn(),
  has: vi.fn(),
  set: vi.fn(),
  delete: vi.fn(),
}));
const redirect = vi.hoisted(() =>
  vi.fn((url: string) => {
    throw new Error(`NEXT_REDIRECT ${url}`);
  }),
);
const fetchMe = vi.hoisted(() => vi.fn());

vi.mock("server-only", () => ({}));
const requestHeaders = new Headers();
vi.mock("next/headers", () => ({
  cookies: async () => store,
  headers: async () => requestHeaders,
}));
vi.mock("next/navigation", () => ({ redirect }));
vi.mock("./api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api")>()),
  fetchMe,
}));

import { ApiError, type Tokens } from "./api";
import { ACCESS_COOKIE, REFRESH_COOKIE } from "./session-cookies";
import {
  clearSessionCookies,
  getAccessToken,
  getCurrentUser,
  getRefreshToken,
  redirectOnUnauthorized,
  setSessionCookies,
} from "./session";

const user = { id: "1", email: "a@b.co", fullName: "A", roles: ["AGENT"] };
const tokens: Tokens = {
  tokenType: "Bearer",
  accessToken: "access",
  expiresIn: 900,
  refreshToken: "refresh",
  user,
};

beforeEach(() => vi.clearAllMocks());

describe("session cookies", () => {
  it("sets access and refresh cookies", async () => {
    await setSessionCookies(tokens);
    expect(store.set).toHaveBeenCalledWith(
      ACCESS_COOKIE,
      "access",
      expect.objectContaining({ httpOnly: true }),
    );
    expect(store.set).toHaveBeenCalledWith(
      REFRESH_COOKIE,
      "refresh",
      expect.objectContaining({ httpOnly: true }),
    );
  });

  it("clears both cookies", async () => {
    await clearSessionCookies();
    expect(store.delete).toHaveBeenCalledWith(ACCESS_COOKIE);
    expect(store.delete).toHaveBeenCalledWith(REFRESH_COOKIE);
  });

  it("reads the refresh token", async () => {
    store.get.mockReturnValue({ value: "refresh" });
    expect(await getRefreshToken()).toBe("refresh");
  });
});

describe("getCurrentUser", () => {
  it("returns the user when the access token is valid", async () => {
    store.get.mockReturnValue({ value: "access" });
    store.has.mockReturnValue(true);
    fetchMe.mockResolvedValue(user);
    expect(await getCurrentUser()).toEqual(user);
  });

  it("redirects to refresh on 401 when a refresh cookie exists", async () => {
    store.get.mockReturnValue({ value: "access" });
    store.has.mockReturnValue(true);
    fetchMe.mockRejectedValue(new ApiError(401, "expired"));
    await expect(getCurrentUser()).rejects.toThrow(
      "NEXT_REDIRECT /session/refresh?next=%2F",
    );
  });

  it("returns to the requested page when the caller gives no path", async () => {
    store.get.mockReturnValue({ value: "access" });
    store.has.mockReturnValue(true);
    fetchMe.mockRejectedValue(new ApiError(401, "expired"));
    requestHeaders.set("x-requested-path", "/organizations/1/products");
    try {
      await expect(getCurrentUser()).rejects.toThrow(
        "NEXT_REDIRECT /session/refresh?next=%2Forganizations%2F1%2Fproducts",
      );
    } finally {
      requestHeaders.delete("x-requested-path");
    }
  });

  it("keeps the query of next intact when refreshing", async () => {
    store.get.mockReturnValue({ value: "access" });
    store.has.mockReturnValue(true);
    fetchMe.mockRejectedValue(new ApiError(401, "expired"));
    await expect(
      getCurrentUser("/invitations/accept?token=a&b=c"),
    ).rejects.toThrow(
      "NEXT_REDIRECT /session/refresh?next=%2Finvitations%2Faccept%3Ftoken%3Da%26b%3Dc",
    );
  });

  it("redirects to login when there are no cookies", async () => {
    store.get.mockReturnValue(undefined);
    store.has.mockReturnValue(false);
    await expect(getCurrentUser()).rejects.toThrow("NEXT_REDIRECT /login");
  });

  it("rethrows errors other than 401", async () => {
    store.get.mockReturnValue({ value: "access" });
    store.has.mockReturnValue(true);
    fetchMe.mockRejectedValue(new ApiError(500, "boom"));
    await expect(getCurrentUser()).rejects.toThrow("boom");
  });
});

describe("getAccessToken", () => {
  it("returns the access token cookie", async () => {
    store.get.mockReturnValue({ value: "access" });
    expect(await getAccessToken()).toBe("access");
  });

  it("redirects to refresh when only the refresh cookie is left", async () => {
    store.get.mockReturnValue(undefined);
    store.has.mockReturnValue(true);
    await expect(getAccessToken("/organizations")).rejects.toThrow(
      "NEXT_REDIRECT /session/refresh?next=%2Forganizations",
    );
  });

  it("redirects to login when there are no cookies", async () => {
    store.get.mockReturnValue(undefined);
    store.has.mockReturnValue(false);
    await expect(getAccessToken()).rejects.toThrow("NEXT_REDIRECT /login");
  });
});

describe("redirectOnUnauthorized", () => {
  const unauthorized = new ApiError(401, "expired");

  it("redirects to the refresh path on 401 when a refresh cookie exists", async () => {
    store.has.mockReturnValue(true);
    requestHeaders.set("x-requested-path", "/organizations/1?x=2");
    await expect(redirectOnUnauthorized(unauthorized)).rejects.toThrow(
      `NEXT_REDIRECT /session/refresh?next=${encodeURIComponent("/organizations/1?x=2")}`,
    );
    requestHeaders.delete("x-requested-path");
  });

  it("redirects to /login on 401 without a refresh cookie", async () => {
    store.has.mockReturnValue(false);
    await expect(redirectOnUnauthorized(unauthorized)).rejects.toThrow(
      "NEXT_REDIRECT /login",
    );
  });

  it("rethrows any other error", async () => {
    const other = new ApiError(500, "boom");
    await expect(redirectOnUnauthorized(other)).rejects.toBe(other);
    expect(redirect).not.toHaveBeenCalled();
  });
});
