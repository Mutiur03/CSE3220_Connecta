import { beforeEach, describe, expect, it, vi } from "vitest";

const store = vi.hoisted(() => ({ get: vi.fn() }));
const fetchMe = vi.hoisted(() => vi.fn());

vi.mock("next/headers", () => ({ cookies: async () => store }));
vi.mock("@/lib/auth-actions", () => ({ logout: vi.fn() }));
vi.mock("@/lib/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api")>()),
  fetchMe,
}));

import { ApiError } from "@/lib/api";
import { ACCESS_COOKIE } from "@/lib/session-cookies";
import { signedInUser } from "./app-rail";

const user = { id: "1", email: "a@b.co", fullName: "A", roles: ["AGENT"] };

beforeEach(() => vi.clearAllMocks());

describe("signedInUser", () => {
  it("is signed out without an access cookie, and never calls the backend", async () => {
    store.get.mockReturnValue(undefined);
    await expect(signedInUser()).resolves.toBeNull();
    expect(store.get).toHaveBeenCalledWith(ACCESS_COOKIE);
    expect(fetchMe).not.toHaveBeenCalled();
  });

  it("returns the user for a valid access token", async () => {
    store.get.mockReturnValue({ value: "token" });
    fetchMe.mockResolvedValue(user);
    await expect(signedInUser()).resolves.toEqual(user);
    expect(fetchMe).toHaveBeenCalledWith("token");
  });

  it("is signed out when the backend rejects the token (401)", async () => {
    store.get.mockReturnValue({ value: "expired" });
    fetchMe.mockRejectedValue(new ApiError(401, "Unauthorized"));
    await expect(signedInUser()).resolves.toBeNull();
  });

  it("rethrows any other failure so the error page handles it", async () => {
    store.get.mockReturnValue({ value: "token" });
    const failure = new ApiError(503, "Service Unavailable");
    fetchMe.mockRejectedValue(failure);
    await expect(signedInUser()).rejects.toBe(failure);
  });
});
