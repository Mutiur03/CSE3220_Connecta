import { beforeEach, describe, expect, it, vi } from "vitest";

const getOrganization = vi.hoisted(() => vi.fn());
const redirectOnUnauthorized = vi.hoisted(() =>
  vi.fn(async (error: unknown) => {
    throw error instanceof Error && error.message === "expired"
      ? new Error("NEXT_REDIRECT")
      : error;
  }),
);

vi.mock("react", () => ({ cache: <T>(fn: T) => fn }));
vi.mock("next/navigation", () => ({ notFound: vi.fn() }));
vi.mock("@/lib/org-api", () => ({ getOrganization }));
vi.mock("@/lib/session", () => ({
  getCurrentUser: async () => ({ id: "u" }),
  getAccessToken: async () => "token",
  redirectOnUnauthorized,
}));

import { ApiError } from "@/lib/api";
import { findOrganization } from "./data";

const orgId = "123e4567-e89b-12d3-a456-426614174000";

beforeEach(() => vi.clearAllMocks());

describe("findOrganization", () => {
  it("is undefined on 404", async () => {
    getOrganization.mockRejectedValue(new ApiError(404, "missing"));
    await expect(findOrganization(orgId)).resolves.toBeUndefined();
    expect(redirectOnUnauthorized).not.toHaveBeenCalled();
  });

  it("hands a 401 to the session redirect", async () => {
    getOrganization.mockRejectedValue(new ApiError(401, "expired"));
    await expect(findOrganization(orgId)).rejects.toThrow("NEXT_REDIRECT");
  });

  it("rethrows other errors", async () => {
    const boom = new ApiError(500, "boom");
    getOrganization.mockRejectedValue(boom);
    await expect(findOrganization(orgId)).rejects.toBe(boom);
  });
});
