import { describe, expect, it, vi } from "vitest";

const request = vi.hoisted(() => vi.fn());

// cache() is a passthrough outside a request, so this stand-in memoizes by argument the way it does
// inside one.
vi.mock("react", () => ({
  cache: <A extends string, R>(fn: (a: A) => R) => {
    const seen = new Map<A, R>();
    return (a: A) => {
      if (!seen.has(a)) seen.set(a, fn(a));
      return seen.get(a) as R;
    };
  },
}));
vi.mock("./api", () => ({ request }));

import { listMemberOrganizations } from "./org-api";

describe("listMemberOrganizations", () => {
  it("asks the backend once for two consumers of the same token", async () => {
    request.mockResolvedValue({ content: [], totalElements: 0 });
    await Promise.all([
      listMemberOrganizations("t"),
      listMemberOrganizations("t"),
    ]);
    expect(request).toHaveBeenCalledTimes(1);
    expect(request.mock.calls[0][0]).toBe("/api/organizations?page=0&size=100");
  });
});
