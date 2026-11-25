// @vitest-environment jsdom
import { describe, expect, it } from "vitest";
import { isTypingTarget, neighbourIndex, shortcutFor } from "./inbox-keys";

const press = (
  key: string,
  mods: Partial<Record<"ctrlKey" | "metaKey" | "altKey", boolean>> = {},
) =>
  shortcutFor({ key, ctrlKey: false, metaKey: false, altKey: false, ...mods });

describe("shortcutFor", () => {
  it("maps the brief's keys, in either case", () => {
    expect(press("j")).toBe("next");
    expect(press("K")).toBe("previous");
    expect(press("e")).toBe("toggleStatus");
    expect(press("r")).toBe("reply");
    expect(press("/")).toBe("search");
    expect(press("?")).toBe("help");
  });

  it("leaves other keys and browser chords alone", () => {
    expect(press("x")).toBeNull();
    expect(press("Enter")).toBeNull();
    expect(press("r", { ctrlKey: true })).toBeNull();
    expect(press("k", { metaKey: true })).toBeNull();
    expect(press("j", { altKey: true })).toBeNull();
  });
});

describe("isTypingTarget", () => {
  it("is true for fields and editable content only", () => {
    expect(isTypingTarget(document.createElement("input"))).toBe(true);
    expect(isTypingTarget(document.createElement("textarea"))).toBe(true);
    expect(isTypingTarget(document.createElement("select"))).toBe(true);
    const editable = document.createElement("div");
    editable.contentEditable = "true";
    Object.defineProperty(editable, "isContentEditable", { value: true });
    expect(isTypingTarget(editable)).toBe(true);
    expect(isTypingTarget(document.createElement("a"))).toBe(false);
    expect(isTypingTarget(null)).toBe(false);
  });
});

describe("neighbourIndex", () => {
  it("moves one row and stops at the ends", () => {
    expect(neighbourIndex(3, 0, 1)).toBe(1);
    expect(neighbourIndex(3, 2, 1)).toBeNull();
    expect(neighbourIndex(3, 1, -1)).toBe(0);
    expect(neighbourIndex(3, 0, -1)).toBeNull();
  });

  it("starts at the top for J and the bottom for K without an open row", () => {
    expect(neighbourIndex(3, -1, 1)).toBe(0);
    expect(neighbourIndex(3, -1, -1)).toBe(2);
    expect(neighbourIndex(0, -1, 1)).toBeNull();
  });
});
