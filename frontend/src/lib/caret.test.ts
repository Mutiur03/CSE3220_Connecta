import { describe, expect, it } from "vitest";
import { insertAtCaret } from "./caret";

describe("insertAtCaret", () => {
  it("inserts at the caret and puts the caret after the insert", () => {
    expect(insertAtCaret("Thanks!", 6, 6, " 🙏")).toEqual({
      value: "Thanks 🙏!",
      caret: 6 + " 🙏".length,
    });
  });

  it("replaces the selected text", () => {
    expect(insertAtCaret("I love it", 2, 6, "❤️")).toEqual({
      value: "I ❤️ it",
      caret: 2 + "❤️".length,
    });
  });

  it("inserts into an empty value", () => {
    expect(insertAtCaret("", 0, 0, "👍")).toEqual({ value: "👍", caret: 2 });
  });

  it("accepts a backwards selection and clamps positions past the end", () => {
    expect(insertAtCaret("abc", 2, 1, "X").value).toBe("aXc");
    expect(insertAtCaret("abc", 9, 9, "X")).toEqual({
      value: "abcX",
      caret: 4,
    });
  });
});
