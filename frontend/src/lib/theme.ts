export const THEME_KEY = "theme";

export type Theme = "light" | "dark";

const ONE_YEAR = 60 * 60 * 24 * 365;

/** A pinned theme from a stored value; anything else means "follow the system". */
export function parseTheme(
  value: string | null | undefined,
): Theme | undefined {
  return value === "light" || value === "dark" ? value : undefined;
}

// The server reads this cookie and renders <html data-theme>, so a pinned theme is right on the
// very first paint. No script has to win a race against the browser.
function writeThemeCookie(theme: Theme | undefined): void {
  if (typeof document === "undefined") return;
  document.cookie = theme
    ? `${THEME_KEY}=${theme}; path=/; max-age=${ONE_YEAR}; samesite=lax`
    : `${THEME_KEY}=; path=/; max-age=0`;
}

export function systemTheme(): Theme {
  return window.matchMedia("(prefers-color-scheme: dark)").matches
    ? "dark"
    : "light";
}

export function renderedTheme(root: HTMLElement): Theme {
  const pinned = root.dataset.theme;
  return pinned === "light" || pinned === "dark" ? pinned : systemTheme();
}

// Switches to the opposite of what is on screen. Picking the system's own theme clears the pin,
// so the page follows the OS again instead of being locked to a value that merely matches it.
export function toggleTheme(root: HTMLElement): void {
  const target: Theme = renderedTheme(root) === "dark" ? "light" : "dark";
  const follow = target === systemTheme();
  if (follow) delete root.dataset.theme;
  else root.dataset.theme = target;
  writeThemeCookie(follow ? undefined : target);
  try {
    if (follow) localStorage.removeItem(THEME_KEY);
    else localStorage.setItem(THEME_KEY, target);
  } catch {
    // Storage blocked: the theme still applies for this page view.
  }
}

// Applies a change made in another tab.
export function syncTheme(root: HTMLElement, stored: string | null): void {
  const theme = parseTheme(stored);
  if (theme) root.dataset.theme = theme;
  else delete root.dataset.theme;
  writeThemeCookie(theme);
}
