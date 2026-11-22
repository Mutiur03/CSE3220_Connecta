import { describe, expect, it } from "vitest";
import { orgIdFromPath, selectCurrentOrganization } from "./current-org";

const FIRST = { id: "11111111-2222-3333-4444-555555555555", name: "First" };
const SECOND = { id: "66666666-7777-8888-9999-000000000000", name: "Second" };
const organizations = [FIRST, SECOND];

describe("orgIdFromPath", () => {
  it("reads the id from organization paths only", () => {
    expect(orgIdFromPath(`/organizations/${FIRST.id}/products/new`)).toBe(FIRST.id);
    expect(orgIdFromPath(`/organizations/${FIRST.id}`)).toBe(FIRST.id);
    expect(orgIdFromPath("/organizations")).toBeUndefined();
    expect(orgIdFromPath("/organizations/not-a-uuid/products")).toBeUndefined();
  });
});

describe("selectCurrentOrganization", () => {
  it("prefers the organization in the URL", () => {
    expect(selectCurrentOrganization(organizations, `/organizations/${SECOND.id}`, FIRST.id)).toBe(SECOND);
  });

  it("falls back to the remembered cookie outside an organization route", () => {
    expect(selectCurrentOrganization(organizations, "/", SECOND.id)).toBe(SECOND);
  });

  it("falls back to the first organization when nothing is remembered", () => {
    expect(selectCurrentOrganization(organizations, "/", undefined)).toBe(FIRST);
    expect(selectCurrentOrganization(organizations, "/", "00000000-0000-0000-0000-000000000000")).toBe(FIRST);
  });

  it("ignores a URL organization the person is not a member of", () => {
    const stranger = "00000000-0000-0000-0000-000000000001";
    expect(selectCurrentOrganization(organizations, `/organizations/${stranger}`, SECOND.id)).toBe(SECOND);
  });

  it("returns undefined when the person has no organizations", () => {
    expect(selectCurrentOrganization([], `/organizations/${FIRST.id}`, FIRST.id)).toBeUndefined();
  });
});
