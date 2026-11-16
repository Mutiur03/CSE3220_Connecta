import { describe, expect, it } from "vitest";
import { NextRequest } from "next/server";
import { proxy } from "./proxy";

const SIGNED_OUT = "http://localhost:3000";
const SIGNED_IN = { cookie: "access_token=a" };

function run(path: string, headers: Record<string, string> = {}) {
  return proxy(new NextRequest(`${SIGNED_OUT}${path}`, { headers }));
}

describe("proxy", () => {
  it("sends a signed-out visitor to login with next = path and query", () => {
    const response = run("/invitations/accept?token=abc_-123");
    const location = new URL(response.headers.get("location") ?? "");
    expect(location.pathname).toBe("/login");
    expect(location.searchParams.get("next")).toBe(
      "/invitations/accept?token=abc_-123",
    );
    expect(response.headers.get("location")).toBe(
      `${SIGNED_OUT}/login?next=%2Finvitations%2Faccept%3Ftoken%3Dabc_-123`,
    );
  });

  it("keeps & and = inside next", () => {
    const location = new URL(
      run("/inbox?tab=open&sort=a=b").headers.get("location") ?? "",
    );
    expect(location.searchParams.get("next")).toBe("/inbox?tab=open&sort=a=b");
  });

  it("does not add next for the home page", () => {
    expect(run("/").headers.get("location")).toBe(`${SIGNED_OUT}/login`);
  });

  it("lets a signed-out visitor load login without a redirect loop", () => {
    expect(run("/login?next=%2Finbox").headers.get("location")).toBeNull();
  });

  it("sends a signed-in visitor on /login to home", () => {
    const response = run("/login", SIGNED_IN);
    expect(response.headers.get("location")).toBe(`${SIGNED_OUT}/`);
  });

  it("sends a signed-in visitor on /login to their next path", () => {
    const response = run("/login?next=%2Forganizations%3Fpage%3D2", SIGNED_IN);
    expect(response.headers.get("location")).toBe(
      `${SIGNED_OUT}/organizations?page=2`,
    );
  });

  it("does not send a signed-in visitor back to /login from /login", () => {
    const response = run("/login?next=%2Flogin%3Fnext%3D%2Finbox", SIGNED_IN);
    expect(response.headers.get("location")).toBe(`${SIGNED_OUT}/`);
  });

  it("keeps a signed-in visitor on /login when the backend was unreachable", () => {
    const response = run("/login?reason=unavailable", {
      cookie: "refresh_token=r",
    });
    expect(response.headers.get("location")).toBeNull();
  });

  it("passes a signed-in visitor through", () => {
    expect(run("/inbox", SIGNED_IN).headers.get("location")).toBeNull();
  });
});
