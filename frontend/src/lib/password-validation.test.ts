import { describe, expect, it } from "vitest";
import { validatePasswordChange } from "./validation";

describe("validatePasswordChange", () => {
  it("accepts a different, long enough, confirmed password", () => {
    expect(
      validatePasswordChange(
        "old-password",
        "new-password-1",
        "new-password-1",
      ),
    ).toEqual({});
  });

  it("asks for every field", () => {
    expect(validatePasswordChange("", "", "")).toEqual({
      currentPassword: "Enter your current password.",
      newPassword: "Enter a new password.",
      confirmPassword: "Repeat the new password.",
    });
  });

  it("applies the signup length limits to the new password", () => {
    expect(validatePasswordChange("old", "short", "short").newPassword).toBe(
      "Use at least 8 characters.",
    );
    expect(
      validatePasswordChange("old", "x".repeat(73), "x".repeat(73)).newPassword,
    ).toBe("Use 72 characters or fewer.");
  });

  it("rejects a new password equal to the current one", () => {
    expect(
      validatePasswordChange("same-password", "same-password", "same-password")
        .newPassword,
    ).toBe("Choose a password different from the current one.");
  });

  it("rejects a confirmation that does not match", () => {
    expect(
      validatePasswordChange("old-password", "new-password-1", "new-password-2")
        .confirmPassword,
    ).toBe("The passwords do not match.");
  });
});
