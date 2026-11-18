// One look for every row in the sidebar: a 36px row with icon and label. Under 800px the sidebar
// is a bottom bar and the row shrinks to a 40px icon button (the label stays for screen readers).
// Active state (selected plus an accent bar on the sidebar edge) is added by RailLink.
export const RAIL_CONTROL =
  "relative flex h-9 w-full items-center gap-2.5 rounded-control px-2.5 text-sm text-secondary transition-[background-color,color,scale] duration-150 ease-out hover:bg-hover hover:text-primary active:scale-[0.98] motion-reduce:transition-none motion-reduce:active:scale-100 max-[799px]:size-10 max-[799px]:w-auto max-[799px]:justify-center max-[799px]:px-0 pointer-coarse:max-[799px]:size-11";

/** The visible label: beside the icon on desktop, screen-reader only in the bottom bar. */
export const RAIL_LABEL = "max-[799px]:sr-only";
