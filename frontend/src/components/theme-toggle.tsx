"use client";

import { useEffect } from "react";
import { syncTheme, THEME_KEY, toggleTheme } from "@/lib/theme";
import { MoonIcon, SunIcon } from "./icons";
import { RAIL_CONTROL, RAIL_LABEL } from "./rail-button";

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
      title="Switch theme"
      className={RAIL_CONTROL}
    >
      <span className="theme-to-dark inline-flex items-center gap-2.5">
        <MoonIcon />
        <span className={RAIL_LABEL}>Dark theme</span>
      </span>
      <span className="theme-to-light inline-flex items-center gap-2.5">
        <SunIcon />
        <span className={RAIL_LABEL}>Light theme</span>
      </span>
    </button>
  );
}
