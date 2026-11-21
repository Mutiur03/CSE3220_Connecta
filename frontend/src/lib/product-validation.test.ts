import { describe, expect, it } from "vitest";
import {
  PRODUCT_DESCRIPTION_MAX_LENGTH,
  PRODUCT_NAME_MAX_LENGTH,
  validateProductDescription,
  validateProductName,
} from "./product-validation";

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
