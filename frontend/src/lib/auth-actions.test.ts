import { beforeEach, describe, expect, it, vi } from "vitest";

const redirect = vi.hoisted(() =>
  vi.fn((url: string) => {
    throw new Error(`NEXT_REDIRECT ${url}`);
  }),
);
const api = vi.hoisted(() => ({
  login: vi.fn(),
  logout: vi.fn(),
  register: vi.fn(),
  setupOwner: vi.fn(),
  acceptInvitationAsNewUser: vi.fn(),
}));
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
vi.mock("./client-headers", () => ({
  forwardedClientHeaders: async () => ({ "User-Agent": "test-agent" }),
}));

import { ApiError } from "./api";
import {
  acceptInvitationAsNewUser,
  login,
  logout,
  setup,
  signup,
} from "./auth-actions";

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
    expect(api.login).toHaveBeenCalledWith("a@b.co", "secret", {
      "User-Agent": "test-agent",
    });
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

function signupForm(next?: string) {
  const data = form(" maya@b.co ", "longenough", next);
  data.set("fullName", " Maya Rahman ");
  return data;
}

describe("signup", () => {
  it("returns field errors without calling the API", async () => {
    const state = await signup({}, form("", ""));
    expect(state.errors?.fullName).toBeDefined();
    expect(api.register).not.toHaveBeenCalled();
  });

  it("registers, signs in and redirects on success", async () => {
    api.register.mockResolvedValue({});
    api.login.mockResolvedValue({ accessToken: "a" });
    await expect(signup({}, signupForm("/organizations"))).rejects.toThrow(
      "NEXT_REDIRECT /organizations",
    );
    expect(api.register).toHaveBeenCalledWith(
      "maya@b.co",
      "longenough",
      "Maya Rahman",
    );
    expect(api.login).toHaveBeenCalledWith("maya@b.co", "longenough", {
      "User-Agent": "test-agent",
    });
    expect(session.setSessionCookies).toHaveBeenCalled();
  });

  it("falls back to / for an unsafe next target", async () => {
    api.register.mockResolvedValue({});
    api.login.mockResolvedValue({ accessToken: "a" });
    await expect(signup({}, signupForm("//evil.example"))).rejects.toThrow(
      "NEXT_REDIRECT /",
    );
  });

  it("puts a duplicate email on the email field and keeps the input", async () => {
    api.register.mockRejectedValue(new ApiError(409, "dup"));
    const state = await signup({}, signupForm());
    expect(state.errors?.email).toBe(
      "An account with this email already exists.",
    );
    expect(state.fullName).toBe("Maya Rahman");
    expect(api.login).not.toHaveBeenCalled();
    expect(redirect).not.toHaveBeenCalled();
  });

  it("reports a generic error on API errors and on network errors", async () => {
    api.register.mockRejectedValueOnce(new ApiError(500, "boom"));
    expect((await signup({}, signupForm())).message).toBe(
      "Something went wrong. Please try again.",
    );
    api.register.mockRejectedValueOnce(new TypeError("fetch failed"));
    expect((await signup({}, signupForm())).message).toBe(
      "Cannot reach the server. Please try again.",
    );
  });
});

describe("setup", () => {
  it("creates the owner, signs in and redirects", async () => {
    api.setupOwner.mockResolvedValue({});
    api.login.mockResolvedValue({ accessToken: "a" });
    await expect(setup({}, signupForm())).rejects.toThrow("NEXT_REDIRECT /");
    expect(api.setupOwner).toHaveBeenCalledWith(
      "maya@b.co",
      "longenough",
      "Maya Rahman",
    );
    expect(api.register).not.toHaveBeenCalled();
    expect(session.setSessionCookies).toHaveBeenCalled();
  });

  it("sends the visitor to signup when an owner already exists (409)", async () => {
    api.setupOwner.mockRejectedValue(new ApiError(409, "done"));
    await expect(setup({}, signupForm())).rejects.toThrow(
      "NEXT_REDIRECT /signup",
    );
    expect(api.login).not.toHaveBeenCalled();
  });

  it("returns field errors without calling the API", async () => {
    const state = await setup({}, form("", ""));
    expect(state.errors).toBeDefined();
    expect(api.setupOwner).not.toHaveBeenCalled();
  });
});

describe("acceptInvitationAsNewUser", () => {
  function inviteForm(password = "longenough") {
    const data = new FormData();
    data.set("token", "tok");
    data.set("fullName", " Sam Agent ");
    data.set("password", password);
    return data;
  }

  it("creates the account, signs in and opens the organizations", async () => {
    api.acceptInvitationAsNewUser.mockResolvedValue({ accessToken: "a" });
    await expect(acceptInvitationAsNewUser({}, inviteForm())).rejects.toThrow(
      "NEXT_REDIRECT /organizations",
    );
    expect(api.acceptInvitationAsNewUser).toHaveBeenCalledWith(
      "tok",
      "Sam Agent",
      "longenough",
    );
    expect(session.setSessionCookies).toHaveBeenCalled();
  });

  it("rejects a short password without calling the API", async () => {
    const state = await acceptInvitationAsNewUser({}, inviteForm("short"));
    expect(state.errors?.password).toBe("Use at least 8 characters.");
    expect(api.acceptInvitationAsNewUser).not.toHaveBeenCalled();
  });

  it.each([
    [409, "already exists"],
    [404, "no longer valid"],
    [422, "no longer valid"],
    [500, "Something went wrong"],
  ])("explains a %i answer", async (status, text) => {
    api.acceptInvitationAsNewUser.mockRejectedValue(new ApiError(status, "x"));
    const state = await acceptInvitationAsNewUser({}, inviteForm());
    expect(state.message).toContain(text);
    expect(state.fullName).toBe("Sam Agent");
  });

  it("reports an unreachable server", async () => {
    api.acceptInvitationAsNewUser.mockRejectedValue(new TypeError("down"));
    expect((await acceptInvitationAsNewUser({}, inviteForm())).message).toBe(
      "Cannot reach the server. Please try again.",
    );
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
