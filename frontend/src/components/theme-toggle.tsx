"use client";

import { useEffect } from "react";
import { syncTheme, THEME_KEY, toggleTheme } from "@/lib/theme";

const ICON =
  "size-5 fill-none stroke-current stroke-2 [stroke-linecap:round] [stroke-linejoin:round]";

// Both labelled icons are in the markup; globals.css hides the one that does not apply, so the
// button is right on first paint and when the OS scheme changes, with no state to hydrate.
export default function ThemeToggle() {
  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.key === THEME_KEY || event.key === null) {
        syncTheme(document.documentElement, event.newValue);
      }
    };
    window.addEventListener("storage", onStorage);
    return () => window.removeEventListener("storage", onStorage);
  }, []);

  return (
    <button
      type="button"
      onClick={() => toggleTheme(document.documentElement)}
      className="inline-flex size-11 items-center justify-center rounded-control text-ink-muted hover:bg-hover hover:text-ink"
    >
      <span className="theme-to-dark inline-flex">
        <svg viewBox="0 0 24 24" aria-hidden="true" className={ICON}>
          <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z" />
        </svg>
        <span className="sr-only">Switch to dark theme</span>
      </span>
      <span className="theme-to-light inline-flex">
        <svg viewBox="0 0 24 24" aria-hidden="true" className={ICON}>
          <circle cx="12" cy="12" r="4" />
          <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
        </svg>
        <span className="sr-only">Switch to light theme</span>
      </span>
    </button>
  );
}
