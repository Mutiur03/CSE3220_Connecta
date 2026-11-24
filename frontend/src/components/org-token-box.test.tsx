import { describe, expect, it } from "vitest";
import { invitationLink } from "./org-token-box";

describe("invitationLink", () => {
  it("points at the accept page with the token as a query value", () => {
    expect(invitationLink("https://app.example", "abc_-123")).toBe(
      "https://app.example/invitations/accept?token=abc_-123",
    );
  });

  it("encodes characters that would break the query string", () => {
    expect(invitationLink("", "a&b=c")).toBe(
      "/invitations/accept?token=a%26b%3Dc",
    );
  });
});
