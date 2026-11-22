import { describe, expect, it } from "vitest";
import { canManageProducts } from "./product-permissions";

describe("canManageProducts", () => {
  it("lets an ADMIN create, edit and delete products", () => {
    expect(canManageProducts("ADMIN")).toBe(true);
  });

  it("keeps an AGENT read-only", () => {
    expect(canManageProducts("AGENT")).toBe(false);
  });
});
