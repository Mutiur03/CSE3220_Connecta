import { describe, expect, it } from "vitest";
import { safeRedirectPath, signedInLoginTarget } from "./redirect";

describe("signedInLoginTarget", () => {
  it.each([
    ["/organizations", "/organizations"],
    ["/organizations?page=2", "/organizations?page=2"],
    [null, "/"],
    ["//evil.example", "/"],
    ["/login", "/"],
    ["/login/?next=%2Finbox", "/"],
  ])("sends a signed-in visitor with next %j to %j", (next, target) => {
    expect(signedInLoginTarget(next)).toBe(target);
  });
});

describe("safeRedirectPath", () => {
  it.each([
    "/",
    "/inbox",
    "/inbox?tab=open",
    "/invitations/accept?token=abc_-123&next=x=y",
    "/inbox?q=a+b%20c#section",
  ])("keeps the same-site path %j", (path) => {
    expect(safeRedirectPath(path)).toBe(path);
  });

  it.each([
    null,
    undefined,
    "",
    "inbox",
    "//evil.example",
    "https://evil.example",
    "/\\evil.example",
    "/\t/evil.example",
    "/\n/evil.example",
    " //evil.example",
    "javascript:alert(1)",
  ])("falls back to / for %j", (path) => {
    expect(safeRedirectPath(path)).toBe("/");
  });
});
