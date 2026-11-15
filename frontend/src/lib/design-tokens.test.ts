import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

// Guards the WCAG 2.1 AA contrast of the token pairs in globals.css, in both
// the light (default) and dark (prefers-color-scheme) palettes.

const css = readFileSync(
  fileURLToPath(new URL("../app/globals.css", import.meta.url)),
  "utf8",
);

const DARK_MARKER = "@media (prefers-color-scheme: dark)";
const [lightSource, darkSource = ""] = css.split(DARK_MARKER);

function readTokens(source: string): Record<string, string> {
  const tokens: Record<string, string> = {};
  for (const [, name, hex] of source.matchAll(
    /--([a-z-]+):\s*(#[0-9a-f]{6})/gi,
  )) {
    tokens[name] = hex;
  }
  return tokens;
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
  ["ink", "canvas", TEXT],
  ["ink", "surface", TEXT],
  ["ink-muted", "canvas", TEXT],
  ["ink-muted", "surface", TEXT],
  ["ink-muted", "hover", TEXT],
  ["danger", "surface", TEXT],
  ["danger", "hover", TEXT],
  ["success", "surface", TEXT],
  ["accent", "surface", TEXT],
  ["accent-ink", "accent", TEXT],
  ["accent-ink", "accent-hover", TEXT],
  ["on-danger", "danger-solid", TEXT],
  ["line-strong", "surface", NON_TEXT],
  ["line-strong", "canvas", NON_TEXT],
  ["focus", "surface", NON_TEXT],
  ["focus", "canvas", NON_TEXT],
];

const palettes = {
  light: readTokens(lightSource),
  dark: readTokens(darkSource),
};

describe.each(Object.entries(palettes))("%s palette contrast", (_, tokens) => {
  it.each(PAIRS)("%s on %s meets %s:1", (fg, bg, min) => {
    expect(tokens[fg], `missing --${fg}`).toBeDefined();
    expect(tokens[bg], `missing --${bg}`).toBeDefined();
    expect(contrast(tokens[fg], tokens[bg])).toBeGreaterThanOrEqual(min);
  });
});
