import { describe, expect, it } from "vitest";
import { alertText, firstFieldError } from "./org-form-state";

describe("firstFieldError", () => {
  it("skips fields without an error", () => {
    expect(firstFieldError({ name: undefined, slug: "Use lowercase." })).toBe(
      "Use lowercase.",
    );
  });

  it("returns undefined when there are no errors", () => {
    expect(firstFieldError(undefined)).toBeUndefined();
    expect(firstFieldError({})).toBeUndefined();
  });
});

describe("alertText", () => {
  it("prefers the action message over a field error", () => {
    expect(
      alertText({ message: "Already used.", errors: { email: "Bad." } }),
    ).toBe("Already used.");
  });

  it("leaves field errors to their field", () => {
    expect(
      alertText({ errors: { token: "Enter the invitation token." } }),
    ).toBeUndefined();
  });

  it("is empty when nothing failed", () => {
    expect(alertText({ notice: "Saved." })).toBeUndefined();
  });
});
