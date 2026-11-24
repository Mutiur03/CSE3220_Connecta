import { describe, expect, it } from "vitest";
import {
  formatCount,
  formatQuantityDelta,
  formatStockValue,
  inventoryLinks,
  movementTypeLabel,
  section,
  stockLine,
} from "./inventory-view";

const ORG = "11111111-2222-3333-4444-555555555555";
const PRODUCT = "66666666-7777-8888-9999-000000000000";

describe("formatStockValue and formatCount", () => {
  it("shows the stock value with two decimals and no grouping, like prices", () => {
    expect(formatStockValue(0)).toBe("0.00");
    expect(formatStockValue(12.5)).toBe("12.50");
    expect(formatStockValue(1234.567)).toBe("1234.57");
  });

  it("groups counts with commas", () => {
    expect(formatCount(0)).toBe("0");
    expect(formatCount(1240)).toBe("1,240");
  });
});

describe("formatQuantityDelta", () => {
  it("signs a change and leaves zero unsigned", () => {
    expect(formatQuantityDelta(5)).toBe("+5");
    expect(formatQuantityDelta(-3)).toBe("-3");
    expect(formatQuantityDelta(0)).toBe("0");
  });
});

describe("stockLine", () => {
  it("shows units left against the reorder level", () => {
    expect(stockLine(2, 5)).toBe("2 left, reorder at 5");
    expect(stockLine(1200, 1500)).toBe("1,200 left, reorder at 1,500");
  });

  it("says none left when the product is out of stock", () => {
    expect(stockLine(0, 5)).toBe("None left, reorder at 5");
  });
});

describe("movementTypeLabel", () => {
  it("names each movement type in plain words", () => {
    expect(movementTypeLabel("RECEIVE")).toBe("Received");
    expect(movementTypeLabel("ISSUE")).toBe("Issued");
    expect(movementTypeLabel("ADJUST")).toBe("Adjusted");
  });
});

describe("inventoryLinks", () => {
  it("filters the product list by each stock status", () => {
    const links = inventoryLinks(ORG);
    expect(links.products).toBe(`/organizations/${ORG}/products`);
    expect(links.lowStock).toBe(
      `/organizations/${ORG}/products?stockStatus=LOW_STOCK`,
    );
    expect(links.outOfStock).toBe(
      `/organizations/${ORG}/products?stockStatus=OUT_OF_STOCK`,
    );
    expect(links.newProduct).toBe(`/organizations/${ORG}/products/new`);
  });

  it("opens a product on its own page", () => {
    expect(inventoryLinks(ORG).product(PRODUCT)).toBe(
      `/organizations/${ORG}/products/${PRODUCT}`,
    );
  });
});

describe("section", () => {
  it("wraps a resolved request as ok with its value", async () => {
    await expect(section(Promise.resolve(7))).resolves.toEqual({
      ok: true,
      value: 7,
    });
  });

  it("turns a failed request into a not-ok section instead of rejecting", async () => {
    await expect(section(Promise.reject(new Error("boom")))).resolves.toEqual({
      ok: false,
    });
  });
});
