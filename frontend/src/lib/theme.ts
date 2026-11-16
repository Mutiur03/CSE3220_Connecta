export const THEME_KEY = "theme";

export type Theme = "light" | "dark";

// Runs inline in <head> before first paint, so a pinned theme never flashes the other one.
// It must stay a plain script: no module, no defer.
export const THEME_INIT_SCRIPT = `try{var t=localStorage.getItem("${THEME_KEY}");if(t==="light"||t==="dark")document.documentElement.dataset.theme=t}catch(e){}`;

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
  try {
    if (follow) localStorage.removeItem(THEME_KEY);
    else localStorage.setItem(THEME_KEY, target);
  } catch {
    // Storage blocked: the theme still applies for this page view.
  }
}

// Applies a change made in another tab.
export function syncTheme(root: HTMLElement, stored: string | null): void {
  if (stored === "light" || stored === "dark") root.dataset.theme = stored;
  else delete root.dataset.theme;
}
