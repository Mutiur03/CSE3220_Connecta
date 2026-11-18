import { describe, expect, it } from "vitest";
import { validateLogin, validateSignup } from "./validation";

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

describe("validateSignup", () => {
  const ok = ["Maya Rahman", "maya@example.com", "longenough"] as const;

  it("accepts valid input", () => {
    expect(validateSignup(...ok)).toEqual({});
  });

  it("requires every field", () => {
    const errors = validateSignup("", "", "");
    expect(Object.keys(errors).sort()).toEqual([
      "email",
      "fullName",
      "password",
    ]);
  });

  it("rejects a short password and values over the backend limits", () => {
    expect(validateSignup(ok[0], ok[1], "short").password).toBe(
      "Use at least 8 characters.",
    );
    expect(
      validateSignup("x".repeat(151), ok[1], ok[2]).fullName,
    ).toBeDefined();
    expect(validateSignup(ok[0], ok[1], "x".repeat(73)).password).toBeDefined();
  });

  it("rejects a malformed email", () => {
    expect(validateSignup(ok[0], "maya@", ok[2]).email).toBe(
      "Enter a valid email address.",
    );
  });
});
