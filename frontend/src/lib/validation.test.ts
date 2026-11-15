import { describe, expect, it } from "vitest";
import { validateLogin } from "./validation";

describe("validateLogin", () => {
  it("accepts a valid email and a password", () => {
    expect(validateLogin("jane@example.com", "secret")).toEqual({});
  });

  it("requires both fields", () => {
    const errors = validateLogin("", "");
    expect(errors.email).toBeDefined();
    expect(errors.password).toBeDefined();
  });

  it.each([
    "jane",
    "jane@",
    "@example.com",
    "jane@example",
    "ja ne@example.com",
  ])("rejects the malformed email %j", (email) => {
    expect(validateLogin(email, "secret").email).toBe(
      "Enter a valid email address.",
    );
  });

  it("does not judge the password beyond presence", () => {
    expect(validateLogin("jane@example.com", "x").password).toBeUndefined();
  });
});
