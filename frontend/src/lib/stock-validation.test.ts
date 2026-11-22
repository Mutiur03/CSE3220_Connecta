import { describe, expect, it } from "vitest";
import {
  SHORT_STOCK_MESSAGE,
  STOCK_REASON_MAX_LENGTH,
  isStockMovementType,
  validateStockQuantity,
  validateStockReason,
} from "./stock-validation";

describe("validateStockQuantity", () => {
  it("needs at least 1 to receive or issue", () => {
    expect(validateStockQuantity("RECEIVE", "1")).toBeUndefined();
    expect(validateStockQuantity("ISSUE", "1")).toBeUndefined();
    expect(validateStockQuantity("RECEIVE", "0")).toBe("Enter a quantity of at least 1.");
    expect(validateStockQuantity("ISSUE", "0")).toBe("Enter a quantity of at least 1.");
  });

  it("lets an adjustment set the level to 0 but not below", () => {
    expect(validateStockQuantity("ADJUST", "0")).toBeUndefined();
    expect(validateStockQuantity("ADJUST", "-1")).toBe("Enter a stock level of 0 or more.");
  });

  it("requires a whole number", () => {
    expect(validateStockQuantity("RECEIVE", "")).toBe("Enter a quantity.");
    expect(validateStockQuantity("RECEIVE", "2.5")).toBe("Enter a whole number, such as 12.");
    expect(validateStockQuantity("ISSUE", "ten")).toBe("Enter a whole number, such as 12.");
  });

  it("rejects a quantity beyond the largest stock the backend stores", () => {
    expect(validateStockQuantity("RECEIVE", "2147483647")).toBeUndefined();
    expect(validateStockQuantity("RECEIVE", "2147483648")).toBe("Use a number up to 2147483647.");
  });
});

describe("validateStockReason", () => {
  it("allows a reason up to the backend limit and rejects longer", () => {
    expect(validateStockReason("")).toBeUndefined();
    expect(validateStockReason("x".repeat(STOCK_REASON_MAX_LENGTH))).toBeUndefined();
    expect(validateStockReason("x".repeat(STOCK_REASON_MAX_LENGTH + 1))).toBe(
      "Use at most 255 characters.",
    );
  });
});

describe("stock movement type and short-stock message", () => {
  it("accepts only the three movement types", () => {
    expect(isStockMovementType("ISSUE")).toBe(true);
    expect(isStockMovementType("TRANSFER")).toBe(false);
  });

  it("explains a short-stock refusal in sentence case", () => {
    expect(SHORT_STOCK_MESSAGE).toMatch(/^Not enough stock/);
  });
});
