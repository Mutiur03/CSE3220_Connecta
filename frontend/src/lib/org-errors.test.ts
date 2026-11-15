import { describe, expect, it } from "vitest";
import { ApiError } from "./api";
import { actionErrorMessage } from "./org-errors";

describe("actionErrorMessage", () => {
  it("passes the backend detail through for a client error", () => {
    expect(
      actionErrorMessage(new ApiError(422, "The last owner cannot leave")),
    ).toBe("The last owner cannot leave");
  });

  it("asks for a reload on 401", () => {
    expect(actionErrorMessage(new ApiError(401, "expired"))).toMatch(
      /session has expired/,
    );
  });

  it("hides server errors behind a generic message", () => {
    expect(actionErrorMessage(new ApiError(500, "SQL: secret"))).toBe(
      "Something went wrong. Please try again.",
    );
  });

  it("reports an unreachable server for anything that is not an API error", () => {
    expect(actionErrorMessage(new TypeError("fetch failed"))).toBe(
      "Cannot reach the server. Please try again.",
    );
  });
});
