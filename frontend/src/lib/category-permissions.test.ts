import { describe, expect, it } from "vitest";
import { canManageCategories } from "./category-permissions";

describe("canManageCategories", () => {
  it("allows an ADMIN", () => expect(canManageCategories("ADMIN")).toBe(true));
  it("denies an AGENT", () => expect(canManageCategories("AGENT")).toBe(false));
});
