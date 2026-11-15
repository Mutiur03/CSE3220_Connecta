import { beforeEach, describe, expect, it, vi } from "vitest";

const redirect = vi.hoisted(() =>
  vi.fn((url: string) => {
    throw new Error(`NEXT_REDIRECT ${url}`);
  }),
);
const api = vi.hoisted(() => ({ login: vi.fn(), logout: vi.fn() }));
const session = vi.hoisted(() => ({
  setSessionCookies: vi.fn(),
  clearSessionCookies: vi.fn(),
  getRefreshToken: vi.fn(),
}));

vi.mock("next/navigation", () => ({ redirect }));
vi.mock("./api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api")>()),
  ...api,
}));
vi.mock("./session", () => session);

import { ApiError } from "./api";
import { login, logout } from "./auth-actions";

function form(email: string, password: string, next?: string) {
  const data = new FormData();
  data.set("email", email);
  data.set("password", password);
  if (next !== undefined) data.set("next", next);
  return data;
}

beforeEach(() => vi.clearAllMocks());

describe("login", () => {
  it("returns field errors without calling the API", async () => {
    const state = await login({}, form("", ""));
    expect(state.errors).toBeDefined();
    expect(api.login).not.toHaveBeenCalled();
  });

  it("sets cookies and redirects home on success", async () => {
    api.login.mockResolvedValue({ accessToken: "a" });
    await expect(login({}, form(" a@b.co ", "secret"))).rejects.toThrow(
      "NEXT_REDIRECT /",
    );
    expect(api.login).toHaveBeenCalledWith("a@b.co", "secret");
    expect(session.setSessionCookies).toHaveBeenCalled();
  });

  it("redirects to a same-site next target with its query intact", async () => {
    api.login.mockResolvedValue({ accessToken: "a" });
    await expect(
      login(
        {},
        form("a@b.co", "secret", "/invitations/accept?token=abc_-123&x=y"),
      ),
    ).rejects.toThrow("NEXT_REDIRECT /invitations/accept?token=abc_-123&x=y");
  });

  it.each([
    "",
    "//evil.example",
    "https://evil.example",
    "/\\evil.example",
    "/\t/evil.example",
    "inbox",
  ])("falls back to / when next is %j", async (next) => {
    api.login.mockResolvedValue({ accessToken: "a" });
    await expect(login({}, form("a@b.co", "secret", next))).rejects.toThrow(
      "NEXT_REDIRECT /",
    );
  });

  it("does not redirect when the credentials are rejected", async () => {
    api.login.mockRejectedValue(new ApiError(401, "no"));
    const state = await login({}, form("a@b.co", "secret", "/inbox"));
    expect(state.message).toBe("Invalid email or password.");
    expect(redirect).not.toHaveBeenCalled();
  });

  it("reports bad credentials on 401", async () => {
    api.login.mockRejectedValue(new ApiError(401, "no"));
    const state = await login({}, form("a@b.co", "secret"));
    expect(state).toEqual({
      message: "Invalid email or password.",
      email: "a@b.co",
    });
  });

  it("reports a generic error on other API errors", async () => {
    api.login.mockRejectedValue(new ApiError(500, "boom"));
    const state = await login({}, form("a@b.co", "secret"));
    expect(state.message).toBe("Something went wrong. Please try again.");
  });

  it("reports an unreachable server on network errors", async () => {
    api.login.mockRejectedValue(new TypeError("fetch failed"));
    const state = await login({}, form("a@b.co", "secret"));
    expect(state.message).toBe("Cannot reach the server. Please try again.");
  });
});

describe("logout", () => {
  it("revokes the refresh token, clears cookies and redirects", async () => {
    session.getRefreshToken.mockResolvedValue("refresh");
    api.logout.mockResolvedValue(undefined);
    await expect(logout()).rejects.toThrow("NEXT_REDIRECT /login");
    expect(api.logout).toHaveBeenCalledWith("refresh");
    expect(session.clearSessionCookies).toHaveBeenCalled();
  });

  it("still signs out locally when the backend is down", async () => {
    session.getRefreshToken.mockResolvedValue("refresh");
    api.logout.mockRejectedValue(new Error("down"));
    await expect(logout()).rejects.toThrow("NEXT_REDIRECT /login");
    expect(session.clearSessionCookies).toHaveBeenCalled();
  });

  it("skips the API call without a refresh token", async () => {
    session.getRefreshToken.mockResolvedValue(undefined);
    await expect(logout()).rejects.toThrow("NEXT_REDIRECT /login");
    expect(api.logout).not.toHaveBeenCalled();
  });
});
