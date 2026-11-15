import { describe, expect, it } from "vitest";
import { accessCookieOptions, refreshCookieOptions } from "./session-cookies";

describe("session cookies", () => {
  it("are httpOnly, same-site lax and site-wide", () => {
    for (const options of [
      accessCookieOptions(900, true),
      refreshCookieOptions(true),
    ]) {
      expect(options).toMatchObject({
        httpOnly: true,
        sameSite: "lax",
        path: "/",
      });
    }
  });

  it("let the access cookie expire with the token", () => {
    expect(accessCookieOptions(900, true).maxAge).toBe(900);
  });

  it("keep the refresh cookie for 7 days, like the backend", () => {
    expect(refreshCookieOptions(true).maxAge).toBe(7 * 24 * 60 * 60);
  });

  it("are only marked secure when asked to (production)", () => {
    expect(accessCookieOptions(900, true).secure).toBe(true);
    expect(refreshCookieOptions(false).secure).toBe(false);
  });
});
