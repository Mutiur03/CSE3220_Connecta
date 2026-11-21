import { describe, expect, it } from "vitest";
import {
  CATEGORY_DESCRIPTION_MAX_LENGTH,
  CATEGORY_NAME_MAX_LENGTH,
  validateCategoryDescription,
  validateCategoryName,
} from "./category-validation";

describe("validateCategoryName", () => {
  it("asks for a name when it is empty", () => {
    expect(validateCategoryName("")).toBe("Enter a name for the category.");
  });

  it("accepts a name up to 100 characters", () => {
    expect(CATEGORY_NAME_MAX_LENGTH).toBe(100);
    expect(validateCategoryName("a".repeat(100))).toBeUndefined();
  });

  it("rejects a name one character over the limit", () => {
    expect(validateCategoryName("a".repeat(101))).toBe("Use at most 100 characters.");
  });
});

describe("validateCategoryDescription", () => {
  it("allows an empty description", () => {
    expect(validateCategoryDescription("")).toBeUndefined();
  });

  it("accepts 500 and rejects 501 characters", () => {
    expect(CATEGORY_DESCRIPTION_MAX_LENGTH).toBe(500);
    expect(validateCategoryDescription("x".repeat(500))).toBeUndefined();
    expect(validateCategoryDescription("x".repeat(501))).toBe("Use at most 500 characters.");
  });
});
