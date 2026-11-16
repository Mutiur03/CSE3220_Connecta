import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

// Guards the WCAG 2.1 AA contrast of the token pairs in globals.css, in both
// the light and dark halves of each light-dark() token.

const css = readFileSync(
  fileURLToPath(new URL("../app/globals.css", import.meta.url)),
  "utf8",
);

const palettes: Record<"light" | "dark", Record<string, string>> = {
  light: {},
  dark: {},
};
for (const [, name, light, dark] of css.matchAll(
  /--([a-z-]+):\s*light-dark\((#[0-9a-f]{6}),\s*(#[0-9a-f]{6})\)/gi,
)) {
  palettes.light[name] = light;
  palettes.dark[name] = dark;
}

function luminance(hex: string): number {
  const [r, g, b] = hex
    .slice(1)
    .match(/../g)!
    .map((pair) => parseInt(pair, 16) / 255)
    .map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

const TEXT = 4.5;
const NON_TEXT = 3;

// [foreground token, background token, minimum ratio]
const PAIRS: Array<[string, string, number]> = [
  ["primary", "app", TEXT],
  ["primary", "surface", TEXT],
  ["primary", "selected", TEXT],
  ["secondary", "app", TEXT],
  ["secondary", "surface", TEXT],
  ["secondary", "hover", TEXT],
  ["secondary", "selected", TEXT],
  ["danger", "surface", TEXT],
  ["danger", "hover", TEXT],
  ["success", "hover", TEXT],
  ["accent", "hover", TEXT],
  ["primary", "hover", TEXT],
  ["success", "surface", TEXT],
  ["accent", "surface", TEXT],
  ["accent", "selected", TEXT],
  ["accent-ink", "accent", TEXT],
  ["accent-ink", "accent-hover", TEXT],
  ["on-danger", "danger-solid", TEXT],
  ["line-strong", "surface", NON_TEXT],
  ["line-strong", "app", NON_TEXT],
  ["focus", "surface", NON_TEXT],
  ["focus", "app", NON_TEXT],
  ["highlight", "surface", NON_TEXT],
  ["highlight", "app", NON_TEXT],
];

describe.each(Object.entries(palettes))("%s palette contrast", (_, tokens) => {
  it.each(PAIRS)("%s on %s meets %s:1", (fg, bg, min) => {
    expect(tokens[fg], `missing --${fg}`).toBeDefined();
    expect(tokens[bg], `missing --${bg}`).toBeDefined();
    expect(contrast(tokens[fg], tokens[bg])).toBeGreaterThanOrEqual(min);
  });
});
