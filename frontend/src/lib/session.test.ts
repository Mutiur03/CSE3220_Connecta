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
vi.mock("next/headers", () => ({ cookies: async () => store }));
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
      "NEXT_REDIRECT /session/refresh?next=/",
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
      "NEXT_REDIRECT /session/refresh?next=/organizations",
    );
  });

  it("redirects to login when there are no cookies", async () => {
    store.get.mockReturnValue(undefined);
    store.has.mockReturnValue(false);
    await expect(getAccessToken()).rejects.toThrow("NEXT_REDIRECT /login");
  });
});
