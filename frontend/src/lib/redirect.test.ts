import { describe, expect, it } from "vitest";
import { safeRedirectPath } from "./redirect";

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
