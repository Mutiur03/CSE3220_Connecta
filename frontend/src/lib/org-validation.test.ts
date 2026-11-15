import { describe, expect, it } from "vitest";
import {
  isUuid,
  validateInviteEmail,
  validateOrganizationName,
  validateSlug,
} from "./org-validation";

describe("validateOrganizationName", () => {
  it("requires a name and caps it at 100 characters", () => {
    expect(validateOrganizationName("")).toBeDefined();
    expect(validateOrganizationName("Acme")).toBeUndefined();
    expect(validateOrganizationName("x".repeat(100))).toBeUndefined();
    expect(validateOrganizationName("x".repeat(101))).toBeDefined();
  });
});

describe("validateSlug", () => {
  it.each(["acme", "acme-team", "team-2"])("accepts %j", (slug) => {
    expect(validateSlug(slug)).toBeUndefined();
  });

  it.each(["Acme", "acme_team", "-acme", "acme-", "acme--team", "acme team"])(
    "rejects %j",
    (slug) => {
      expect(validateSlug(slug)).toBeDefined();
    },
  );

  it("caps the slug at 60 characters", () => {
    expect(validateSlug("a".repeat(60))).toBeUndefined();
    expect(validateSlug("a".repeat(61))).toBeDefined();
  });
});

describe("validateInviteEmail", () => {
  it("accepts a plausible address and rejects a malformed one", () => {
    expect(validateInviteEmail("jane@example.com")).toBeUndefined();
    expect(validateInviteEmail("jane@example")).toBeDefined();
    expect(validateInviteEmail("")).toBeDefined();
  });
});

describe("isUuid", () => {
  it("accepts a UUID and rejects anything else", () => {
    expect(isUuid("3f2504e0-4f89-11d3-9a0c-0305e82c3301")).toBe(true);
    expect(isUuid("../admin")).toBe(false);
    expect(isUuid("not-a-uuid")).toBe(false);
  });
});
