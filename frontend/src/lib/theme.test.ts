import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  renderedTheme,
  syncTheme,
  parseTheme,
  THEME_KEY,
  toggleTheme,
} from "./theme";

const storage = new Map<string, string>();

function stubBrowser(systemDark: boolean) {
  vi.stubGlobal("window", {
    matchMedia: () => ({ matches: systemDark }),
  });
  vi.stubGlobal("localStorage", {
    getItem: (key: string) => storage.get(key) ?? null,
    setItem: (key: string, value: string) => storage.set(key, value),
    removeItem: (key: string) => storage.delete(key),
  });
}

const root = () => ({ dataset: {} as Record<string, string | undefined> });

beforeEach(() => storage.clear());
afterEach(() => vi.unstubAllGlobals());

describe("renderedTheme", () => {
  it("follows the system when nothing is pinned", () => {
    stubBrowser(true);
    expect(renderedTheme(root() as unknown as HTMLElement)).toBe("dark");
  });

  it("prefers the pinned theme over the system", () => {
    stubBrowser(true);
    const el = root();
    el.dataset.theme = "light";
    expect(renderedTheme(el as unknown as HTMLElement)).toBe("light");
  });
});

describe("toggleTheme", () => {
  it("pins the opposite of the system theme", () => {
    stubBrowser(false);
    const el = root();
    toggleTheme(el as unknown as HTMLElement);
    expect(el.dataset.theme).toBe("dark");
    expect(storage.get(THEME_KEY)).toBe("dark");
  });

  it("clears the pin when toggling back to the system theme", () => {
    stubBrowser(false);
    const el = root();
    toggleTheme(el as unknown as HTMLElement);
    toggleTheme(el as unknown as HTMLElement);
    expect(el.dataset.theme).toBeUndefined();
    expect(storage.has(THEME_KEY)).toBe(false);
  });

  it("still applies the theme when storage throws", () => {
    stubBrowser(false);
    vi.stubGlobal("localStorage", {
      setItem: () => {
        throw new Error("blocked");
      },
      removeItem: () => {
        throw new Error("blocked");
      },
    });
    const el = root();
    toggleTheme(el as unknown as HTMLElement);
    expect(el.dataset.theme).toBe("dark");
  });
});

describe("syncTheme", () => {
  it("applies a theme stored by another tab", () => {
    const el = root();
    syncTheme(el as unknown as HTMLElement, "dark");
    expect(el.dataset.theme).toBe("dark");
  });

  it("clears the pin when the other tab removed it", () => {
    const el = root();
    el.dataset.theme = "dark";
    syncTheme(el as unknown as HTMLElement, null);
    expect(el.dataset.theme).toBeUndefined();
  });
});

describe("parseTheme", () => {
  it("accepts light and dark and nothing else", () => {
    expect(parseTheme("light")).toBe("light");
    expect(parseTheme("dark")).toBe("dark");
    expect(parseTheme("sepia")).toBeUndefined();
    expect(parseTheme(undefined)).toBeUndefined();
    expect(parseTheme(null)).toBeUndefined();
  });
});
