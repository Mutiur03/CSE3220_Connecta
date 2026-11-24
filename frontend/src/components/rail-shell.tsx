"use client";

import Link from "next/link";
import { useEffect, useState, type ReactNode } from "react";
import BrandMark from "./brand-mark";
import { CloseIcon, MenuIcon } from "./icons";

const ICON_BUTTON =
  "inline-flex size-10 items-center justify-center rounded-control text-secondary transition-colors duration-150 ease-out hover:bg-hover hover:text-primary motion-reduce:transition-none pointer-coarse:size-11";

/**
 * The frame of the sidebar. From 800px it is a fixed 240px column. Below that the page gets a
 * top bar with a menu button, and the same sidebar slides in over the page as a drawer.
 */
export default function RailShell({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    document.addEventListener("keydown", onKeyDown);
    // Keep the page from scrolling behind the drawer.
    const { overflow } = document.body.style;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKeyDown);
      document.body.style.overflow = overflow;
    };
  }, [open]);

  return (
    <>
      <header className="sticky top-0 z-20 flex h-14 shrink-0 items-center gap-2 border-b border-line bg-surface px-2 min-[800px]:hidden">
        <button
          type="button"
          aria-label="Open menu"
          aria-expanded={open}
          aria-controls="main-nav"
          onClick={() => setOpen(true)}
          className={ICON_BUTTON}
        >
          <MenuIcon />
        </button>
        <Link href="/" className="flex items-center gap-2 rounded-control px-1">
          <BrandMark className="size-7" />
          <span className="text-base font-medium">Connecta</span>
        </Link>
      </header>

      <button
        type="button"
        aria-hidden="true"
        tabIndex={-1}
        onClick={() => setOpen(false)}
        className={`fixed inset-0 z-30 bg-black/40 transition-opacity duration-200 ease-out motion-reduce:transition-none min-[800px]:hidden ${
          open ? "opacity-100" : "pointer-events-none opacity-0"
        }`}
      />

      <nav
        id="main-nav"
        aria-label="Main"
        onClick={(event) => {
          if ((event.target as HTMLElement).closest("a")) setOpen(false);
        }}
        className={`fixed inset-y-0 left-0 z-40 flex w-72 max-w-[85vw] flex-col gap-6 overflow-y-auto overscroll-contain border-r border-line bg-surface px-3 py-4 shadow-lg transition-[translate,visibility] duration-200 ease-out will-change-[translate] motion-reduce:transition-none min-[800px]:visible min-[800px]:sticky min-[800px]:top-0 min-[800px]:z-20 min-[800px]:h-dvh min-[800px]:w-60 min-[800px]:max-w-none min-[800px]:shrink-0 min-[800px]:translate-x-0 min-[800px]:self-start min-[800px]:shadow-none min-[800px]:transition-none ${
          open ? "translate-x-0" : "invisible -translate-x-full"
        }`}
      >
        <div className="flex items-center justify-between gap-2">
          <Link
            href="/"
            className="flex items-center gap-2.5 rounded-control px-2.5"
          >
            <BrandMark className="size-9" />
            <span className="text-lg font-medium">Connecta</span>
          </Link>
          <button
            type="button"
            aria-label="Close menu"
            onClick={() => setOpen(false)}
            className={`${ICON_BUTTON} min-[800px]:hidden`}
          >
            <CloseIcon />
          </button>
        </div>
        {children}
      </nav>
    </>
  );
}
