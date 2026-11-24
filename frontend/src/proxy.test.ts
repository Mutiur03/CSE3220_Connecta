import { describe, expect, it } from "vitest";
import { NextRequest } from "next/server";
import { proxy } from "./proxy";

const SIGNED_OUT = "http://localhost:3000";
const SIGNED_IN = { cookie: "access_token=a" };

async function run(path: string, headers: Record<string, string> = {}) {
  return await proxy(new NextRequest(`${SIGNED_OUT}${path}`, { headers }));
}

describe("proxy", () => {
  it("sends a signed-out visitor to login with next = path and query", async () => {
    const response = await run("/organizations/abc?tab=members");
    const location = new URL(response.headers.get("location") ?? "");
    expect(location.pathname).toBe("/login");
    expect(location.searchParams.get("next")).toBe(
      "/organizations/abc?tab=members",
    );
    expect(response.headers.get("location")).toBe(
      `${SIGNED_OUT}/login?next=%2Forganizations%2Fabc%3Ftab%3Dmembers`,
    );
  });

  it("keeps & and = inside next", async () => {
    const location = new URL(
      (await run("/inbox?tab=open&sort=a=b")).headers.get("location") ?? "",
    );
    expect(location.searchParams.get("next")).toBe("/inbox?tab=open&sort=a=b");
  });

  it("shows the landing page at / to a signed-out visitor, without a redirect", async () => {
    const response = await run("/");
    expect(response.headers.get("location")).toBeNull();
    expect(response.headers.get("x-middleware-rewrite")).toBe(
      `${SIGNED_OUT}/welcome`,
    );
  });

  it("sends a signed-in visitor on /welcome home", async () => {
    expect((await run("/welcome", SIGNED_IN)).headers.get("location")).toBe(
      `${SIGNED_OUT}/`,
    );
  });

  it("lets a signed-out visitor load login without a redirect loop", async () => {
    expect(
      (await run("/login?next=%2Finbox")).headers.get("location"),
    ).toBeNull();
  });

  it("sends a signed-in visitor on /login to home", async () => {
    const response = await run("/login", SIGNED_IN);
    expect(response.headers.get("location")).toBe(`${SIGNED_OUT}/`);
  });

  it("sends a signed-in visitor on /login to their next path", async () => {
    const response = await run(
      "/login?next=%2Forganizations%3Fpage%3D2",
      SIGNED_IN,
    );
    expect(response.headers.get("location")).toBe(
      `${SIGNED_OUT}/organizations?page=2`,
    );
  });

  it("does not send a signed-in visitor back to /login from /login", async () => {
    const response = await run(
      "/login?next=%2Flogin%3Fnext%3D%2Finbox",
      SIGNED_IN,
    );
    expect(response.headers.get("location")).toBe(`${SIGNED_OUT}/`);
  });

  it("keeps a signed-in visitor on /login when the backend was unreachable", async () => {
    const response = await run("/login?reason=unavailable", {
      cookie: "refresh_token=r",
    });
    expect(response.headers.get("location")).toBeNull();
  });

  it("lets a signed-out visitor open an invitation link", async () => {
    expect(
      (await run("/invitations/accept?token=abc_-123")).headers.get("location"),
    ).toBeNull();
  });

  it("lets a signed-out visitor load setup, and sends a signed-in one home", async () => {
    expect((await run("/setup")).headers.get("location")).toBeNull();
    expect((await run("/setup", SIGNED_IN)).headers.get("location")).toBe(
      `${SIGNED_OUT}/`,
    );
  });

  it("lets a signed-out visitor load signup", async () => {
    expect((await run("/signup")).headers.get("location")).toBeNull();
  });

  it("sends a signed-in visitor on /signup to home", async () => {
    expect((await run("/signup", SIGNED_IN)).headers.get("location")).toBe(
      `${SIGNED_OUT}/`,
    );
  });

  it("passes a signed-in visitor through", async () => {
    expect((await run("/inbox", SIGNED_IN)).headers.get("location")).toBeNull();
  });
});
