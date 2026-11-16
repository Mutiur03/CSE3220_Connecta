import { describe, expect, it } from "vitest";
import {
  MAX_PAGE_INDEX,
  pageCount,
  pageHref,
  pageRange,
  parsePageParam,
} from "./org-pagination";

describe("parsePageParam", () => {
  it("reads a whole number from zero up", () => {
    expect(parsePageParam("0")).toBe(0);
    expect(parsePageParam("3")).toBe(3);
  });

  it("shows the first page for anything else", () => {
    for (const raw of [undefined, "", "abc", "-1", "1.5", "NaN", "Infinity"]) {
      expect(parsePageParam(raw)).toBe(0);
    }
  });

  it("uses the first value of a repeated param", () => {
    expect(parsePageParam(["2", "5"])).toBe(2);
  });

  it("caps a huge page number", () => {
    expect(parsePageParam("99999999999999")).toBe(MAX_PAGE_INDEX);
  });
});

describe("pageCount", () => {
  it("rounds up and never reports zero pages", () => {
    expect(pageCount(0, 20)).toBe(1);
    expect(pageCount(20, 20)).toBe(1);
    expect(pageCount(21, 20)).toBe(2);
    expect(pageCount(59, 20)).toBe(3);
  });
});

describe("pageRange", () => {
  it("counts the rows shown on the first and last pages", () => {
    expect(pageRange(0, 20, 29)).toEqual({ from: 1, to: 20 });
    expect(pageRange(1, 20, 29)).toEqual({ from: 21, to: 29 });
  });

  it("is null when there is nothing to show", () => {
    expect(pageRange(0, 20, 0)).toBeNull();
    expect(pageRange(3, 20, 29)).toBeNull();
  });
});

describe("pageHref", () => {
  it("sets only the page that changed and keeps the other list's page", () => {
    const href = pageHref(
      "/organizations/1",
      { membersPage: 2, invitationsPage: 1 },
      "membersPage",
      3,
    );
    expect(href).toBe("/organizations/1?membersPage=3&invitationsPage=1");
  });

  it("leaves page 0 out of the query", () => {
    const href = pageHref(
      "/organizations/1",
      { membersPage: 1, invitationsPage: 0 },
      "membersPage",
      0,
    );
    expect(href).toBe("/organizations/1");
  });

  it("keeps the path when no page is set", () => {
    expect(pageHref("/organizations", { page: 0 }, "page", 1)).toBe(
      "/organizations?page=1",
    );
  });
});
