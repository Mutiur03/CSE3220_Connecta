import { describe, expect, it } from "vitest";
import {
  PRODUCT_DESCRIPTION_MAX_LENGTH,
  PRODUCT_NAME_MAX_LENGTH,
  PRODUCT_QUERY_MAX_LENGTH,
  PRODUCT_SKU_MAX_LENGTH,
  hasProductFilters,
  parseProductFilters,
  productListQuery,
  productsHref,
  parseReorderLevel,
  parseUnitPrice,
  validateProductDescription,
  validateProductName,
  validateProductSku,
  validateReorderLevel,
  validateUnitPrice,
} from "./product-validation";

const CATEGORY = "11111111-1111-1111-1111-111111111111";
const PRICE_MESSAGE = "Enter a price of 0 or more, such as 12.50.";

describe("validateProductName", () => {
  it("asks for a name when it is empty", () => {
    expect(validateProductName("")).toBe("Enter a name for the product.");
  });

  it("accepts a name up to the backend limit", () => {
    expect(validateProductName("a".repeat(PRODUCT_NAME_MAX_LENGTH))).toBeUndefined();
  });

  it("rejects a name one character over the limit", () => {
    expect(validateProductName("a".repeat(PRODUCT_NAME_MAX_LENGTH + 1))).toBe(
      `Use at most ${PRODUCT_NAME_MAX_LENGTH} characters.`,
    );
  });
});

describe("validateProductDescription", () => {
  it("allows an empty description", () => {
    expect(validateProductDescription("")).toBeUndefined();
  });

  it("rejects a description over the limit", () => {
    expect(
      validateProductDescription("x".repeat(PRODUCT_DESCRIPTION_MAX_LENGTH + 1)),
    ).toBe(`Use at most ${PRODUCT_DESCRIPTION_MAX_LENGTH} characters.`);
  });
});

describe("validateProductSku", () => {
  it("allows a blank SKU, which means none", () => {
    expect(validateProductSku("")).toBeUndefined();
  });

  it("accepts a SKU of exactly 64 characters", () => {
    expect(validateProductSku("S".repeat(PRODUCT_SKU_MAX_LENGTH))).toBeUndefined();
  });

  it("rejects a SKU one character over 64", () => {
    expect(validateProductSku("S".repeat(PRODUCT_SKU_MAX_LENGTH + 1))).toBe(
      "Use at most 64 characters.",
    );
  });
});

describe("validateUnitPrice", () => {
  it("treats a blank price as 0.00", () => {
    expect(validateUnitPrice("")).toBeUndefined();
    expect(parseUnitPrice("")).toBe(0);
  });

  it("accepts whole numbers and up to two decimal places", () => {
    expect(validateUnitPrice("0")).toBeUndefined();
    expect(validateUnitPrice("12")).toBeUndefined();
    expect(validateUnitPrice("12.5")).toBeUndefined();
    expect(validateUnitPrice("12.50")).toBeUndefined();
    expect(parseUnitPrice("12.50")).toBe(12.5);
  });

  it("rejects a third decimal place", () => {
    expect(validateUnitPrice("1.234")).toBe("Use at most 2 decimal places.");
  });

  it("rejects a negative price and text that is not a number", () => {
    expect(validateUnitPrice("-1")).toBe(PRICE_MESSAGE);
    expect(validateUnitPrice("abc")).toBe(PRICE_MESSAGE);
    expect(validateUnitPrice(".5")).toBe(PRICE_MESSAGE);
  });

  it("rejects more than ten digits before the decimal point", () => {
    expect(validateUnitPrice("12345678901")).toBe(
      "Use at most 10 digits before the decimal point.",
    );
    expect(validateUnitPrice("1234567890.12")).toBeUndefined();
  });
});

describe("validateReorderLevel", () => {
  it("treats a blank reorder level as 0", () => {
    expect(validateReorderLevel("")).toBeUndefined();
    expect(parseReorderLevel("")).toBe(0);
  });

  it("accepts whole numbers from 0", () => {
    expect(validateReorderLevel("0")).toBeUndefined();
    expect(parseReorderLevel("15")).toBe(15);
  });

  it("rejects decimals, negatives and text", () => {
    expect(validateReorderLevel("2.5")).toBe("Enter a whole number, 0 or more.");
    expect(validateReorderLevel("-3")).toBe("Enter a whole number, 0 or more.");
    expect(validateReorderLevel("ten")).toBe("Enter a whole number, 0 or more.");
  });

  it("rejects a number past the backend int limit", () => {
    expect(validateReorderLevel("2147483648")).toBe("Use a number up to 2147483647.");
  });
});

describe("parseProductFilters", () => {
  const categories = [CATEGORY];

  it("reads the search, category and stock status from the URL", () => {
    expect(
      parseProductFilters(
        { q: "  oat ", categoryId: CATEGORY, stockStatus: "LOW_STOCK" },
        categories,
      ),
    ).toEqual({ q: "oat", categoryId: CATEGORY, stockStatus: "LOW_STOCK" });
  });

  it("ignores an unknown stock status so it is never sent to the API", () => {
    expect(parseProductFilters({ stockStatus: "BROKEN" }, categories).stockStatus).toBeUndefined();
  });

  it("ignores a category that is not in the organization's list", () => {
    expect(
      parseProductFilters({ categoryId: "22222222-2222-2222-2222-222222222222" }, categories),
    ).toEqual({ q: undefined, categoryId: undefined, stockStatus: undefined });
  });

  it("reads only the first value of a repeated parameter", () => {
    expect(parseProductFilters({ q: ["a", "b"] }, categories).q).toBe("a");
  });

  it("caps a long search and treats a blank search as no search", () => {
    expect(parseProductFilters({ q: "x".repeat(500) }, categories).q).toHaveLength(
      PRODUCT_QUERY_MAX_LENGTH,
    );
    expect(parseProductFilters({ q: "   " }, categories).q).toBeUndefined();
  });

  it("reports whether any filter is set", () => {
    expect(hasProductFilters(parseProductFilters({}, categories))).toBe(false);
    expect(hasProductFilters(parseProductFilters({ q: "oat" }, categories))).toBe(true);
  });
});

describe("productListQuery and productsHref", () => {
  it("leaves out unset filters and page 0", () => {
    expect(productListQuery({}, 0)).toBe("");
    expect(productsHref("/products", {})).toBe("/products");
  });

  it("keeps the filters and the page together, so pagination keeps them", () => {
    const filters = { q: "oat milk", stockStatus: "LOW_STOCK" as const };
    expect(productsHref("/products", filters, 2)).toBe(
      "/products?q=oat+milk&stockStatus=LOW_STOCK&page=2",
    );
  });

  it("adds the size for the API call", () => {
    expect(productListQuery({ categoryId: CATEGORY }, 0, 20)).toBe(
      `categoryId=${CATEGORY}&size=20`,
    );
  });
});
