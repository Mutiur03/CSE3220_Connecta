import { NextRequest } from "next/server";
import { beforeEach, describe, expect, it, vi } from "vitest";

const refresh = vi.hoisted(() => vi.fn());

vi.mock("@/lib/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api")>()),
  refresh,
}));

import { ApiError } from "@/lib/api";
import { proxy } from "./proxy";

const TOKENS = {
  accessToken: "new-access",
  refreshToken: "new-refresh",
  expiresIn: 300,
};

let counter = 0;
// A fresh refresh token per call: the shared in-flight result is keyed by token.
function run(path: string, cookie = `refresh_token=r${++counter}`) {
  return proxy(
    new NextRequest(`http://localhost:3000${path}`, {
      headers: { cookie, "user-agent": "test-agent" },
    }),
  );
}

describe("proxy refresh", () => {
  beforeEach(() => {
    refresh.mockReset();
  });

  it("rotates an expired session before the page renders and hands the render the new cookies", async () => {
    refresh.mockResolvedValue(TOKENS);

    const response = await run("/organizations/1/products");

    expect(refresh).toHaveBeenCalledWith(expect.stringMatching(/^r\d+$/), {
      "User-Agent": "test-agent",
    });
    expect(response.headers.get("location")).toBeNull();
    expect(response.cookies.get("access_token")?.value).toBe("new-access");
    expect(response.cookies.get("refresh_token")?.value).toBe("new-refresh");
    const seenByRender = response.headers.get("x-middleware-request-cookie");
    expect(seenByRender).toContain("access_token=new-access");
    expect(seenByRender).toContain("refresh_token=new-refresh");
  });

  it("does not call the backend while the access cookie is still there", async () => {
    await run("/inbox", "access_token=a; refresh_token=r");

    expect(refresh).not.toHaveBeenCalled();
  });

  it("does not refresh on the sign-in pages", async () => {
    await run("/login?reason=unavailable");

    expect(refresh).not.toHaveBeenCalled();
  });

  it("clears both cookies when the refresh token is rejected", async () => {
    refresh.mockRejectedValue(new ApiError(401, "invalid"));

    const response = await run("/inbox");

    expect(response.cookies.get("access_token")?.value).toBe("");
    expect(response.cookies.get("refresh_token")?.value).toBe("");
  });

  it.each([
    ["a concurrent rotation", new ApiError(409, "rotated")],
    ["a backend outage", new ApiError(503, "down")],
    ["an unreachable backend", new TypeError("fetch failed")],
  ])("keeps the cookies and renders on %s", async (_name, error) => {
    refresh.mockRejectedValue(error);

    const response = await run("/inbox");

    expect(response.headers.get("location")).toBeNull();
    expect(response.cookies.get("access_token")).toBeUndefined();
    expect(response.cookies.get("refresh_token")).toBeUndefined();
  });
});
