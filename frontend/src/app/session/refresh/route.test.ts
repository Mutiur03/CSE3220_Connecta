import { NextRequest } from "next/server";
import { beforeEach, describe, expect, it, vi } from "vitest";

const refresh = vi.hoisted(() => vi.fn());

vi.mock("@/lib/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api")>()),
  refresh,
}));

vi.mock("@/lib/client-headers", () => ({
  forwardedClientHeaders: async () => ({ "User-Agent": "test-agent" }),
}));

import { ApiError } from "@/lib/api";
import { GET } from "./route";

const ORIGIN = "http://0.0.0.0:3000";
const TOKENS = {
  accessToken: "new-access",
  refreshToken: "new-refresh",
  expiresIn: 900,
};

function request(query: string, cookie?: string): NextRequest {
  return new NextRequest(`${ORIGIN}/session/refresh${query}`, {
    headers: cookie ? { cookie } : undefined,
  });
}

function expectRelative(location: string | null): void {
  expect(location).not.toBeNull();
  expect(location).not.toContain("0.0.0.0");
  expect(location).not.toMatch(/^[a-z]+:\/\//i);
  expect(location?.startsWith("/")).toBe(true);
}

describe("GET /session/refresh", () => {
  beforeEach(() => {
    refresh.mockReset();
  });

  it("rotates tokens, sets both cookies and redirects to the relative next path", async () => {
    refresh.mockResolvedValue(TOKENS);

    const response = await GET(
      request("?next=%2Forganizations", "refresh_token=old"),
    );

    expect(refresh).toHaveBeenCalledWith("old", { "User-Agent": "test-agent" });
    expect(response.status).toBe(307);
    expect(response.headers.get("location")).toBe("/organizations");
    expectRelative(response.headers.get("location"));
    expect(response.cookies.get("access_token")?.value).toBe("new-access");
    expect(response.cookies.get("refresh_token")?.value).toBe("new-refresh");
  });

  it("shares one backend call between concurrent requests with the same token", async () => {
    refresh.mockResolvedValue(TOKENS);

    const [a, b] = await Promise.all([
      GET(request("?next=%2Fa", "refresh_token=shared")),
      GET(request("?next=%2Fb", "refresh_token=shared")),
    ]);

    expect(refresh).toHaveBeenCalledTimes(1);
    expect(a.cookies.get("refresh_token")?.value).toBe("new-refresh");
    expect(b.cookies.get("refresh_token")?.value).toBe("new-refresh");
  });

  it("keeps the cookies and returns to next on a 409 from a concurrent rotation", async () => {
    refresh.mockRejectedValue(new ApiError(409, "rotated"));

    const response = await GET(request("?next=%2Fa", "refresh_token=raced"));

    expect(response.headers.get("location")).toBe("/a");
    expect(response.cookies.get("refresh_token")).toBeUndefined();
    expect(response.cookies.get("access_token")).toBeUndefined();
  });

  it("redirects to /login without calling the API when there is no refresh cookie", async () => {
    const response = await GET(request("?next=%2F"));

    expect(refresh).not.toHaveBeenCalled();
    expect(response.headers.get("location")).toBe("/login");
    expectRelative(response.headers.get("location"));
    expect(response.cookies.get("access_token")).toBeUndefined();
  });

  it("clears both cookies and redirects to /login when the refresh token is rejected", async () => {
    refresh.mockRejectedValue(new ApiError(401, "expired"));

    const response = await GET(request("?next=%2F", "refresh_token=rejected"));

    expect(response.headers.get("location")).toBe("/login");
    expectRelative(response.headers.get("location"));
    expect(response.cookies.get("access_token")?.value).toBe("");
    expect(response.cookies.get("refresh_token")?.value).toBe("");
  });

  it.each([
    ["a 503 from the backend", new ApiError(503, "unavailable")],
    ["an unreachable backend", new TypeError("fetch failed")],
  ])(
    "flags the outage on /login without clearing cookies for %s",
    async (_case, failure) => {
      refresh.mockRejectedValue(failure);

      const response = await GET(
        request("?next=%2F", `refresh_token=down-${Math.random()}`),
      );

      expect(response.headers.get("location")).toBe(
        "/login?reason=unavailable",
      );
      expectRelative(response.headers.get("location"));
      expect(response.cookies.get("access_token")).toBeUndefined();
      expect(response.cookies.get("refresh_token")).toBeUndefined();
    },
  );

  it.each(["//evil.com", "https://evil.com", "/\\evil.com"])(
    "falls back to / for an unsafe next value %s",
    async (next) => {
      refresh.mockResolvedValue(TOKENS);

      const response = await GET(
        request(
          `?next=${encodeURIComponent(next)}`,
          `refresh_token=n-${Math.random()}`,
        ),
      );

      expect(response.headers.get("location")).toBe("/");
      expectRelative(response.headers.get("location"));
    },
  );
});
