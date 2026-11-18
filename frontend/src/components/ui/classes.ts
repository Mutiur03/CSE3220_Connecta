// Pure class-string builders for the base components. No React here, so the
// org-styles helpers and server components can share them.

export type ButtonTone = "primary" | "secondary" | "danger" | "destructive";
export type BadgeTone = "neutral" | "accent" | "success" | "danger";
export type AlertTone = "info" | "success" | "danger";

const BUTTON_BASE =
  "inline-flex items-center justify-center gap-2 rounded-control text-sm font-medium whitespace-nowrap transition-[background-color,color,scale] duration-150 ease-out active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-50 motion-reduce:transition-none motion-reduce:active:scale-100 pointer-coarse:min-h-11";

const BUTTON_TONES: Record<ButtonTone, string> = {
  primary: "bg-primary text-surface hover:bg-primary/85",
  secondary: "border border-line bg-surface text-primary hover:bg-hover",
  destructive: "bg-danger-solid text-on-danger hover:bg-danger-solid/85",
  danger:
    "border border-line bg-surface text-danger hover:border-danger/50 hover:bg-danger/8",
};

export type ButtonSize = "md" | "sm";

// md lines up with a field (40px); sm is for buttons that sit inside a table row.
const BUTTON_SIZES: Record<ButtonSize, string> = {
  md: "min-h-10 px-4",
  sm: "min-h-8 px-3",
};

export const buttonClasses = (
  tone: ButtonTone = "secondary",
  size: ButtonSize = "md",
) => `${BUTTON_BASE} ${BUTTON_SIZES[size]} ${BUTTON_TONES[tone]}`;

export const fieldClasses =
  "min-h-10 w-full min-w-0 rounded-control border border-line-strong bg-surface px-3 py-2 text-sm text-primary transition-colors duration-150 ease-out placeholder:text-secondary hover:border-secondary motion-reduce:transition-none aria-invalid:border-danger pointer-coarse:min-h-11";

// Badges are full-radius tags. Neutral sits on bg-hover; success and danger are a 12% tint of
// their own colour, the way a change reads on a stat ("+38%", "-67%").
const BADGE_TONES: Record<BadgeTone, string> = {
  neutral: "bg-hover text-secondary",
  accent: "bg-accent/12 text-accent",
  success: "bg-success/12 text-success",
  danger: "bg-danger/12 text-danger",
};

export const badgeClasses = (tone: BadgeTone = "neutral") =>
  `inline-flex items-center rounded-full px-2 py-0.5 text-caption font-medium ${BADGE_TONES[tone]}`;

const ALERT_TONES: Record<AlertTone, string> = {
  info: "border-line-strong text-primary",
  success: "border-success text-success",
  danger: "border-danger text-danger",
};

export const alertClasses = (tone: AlertTone = "info") =>
  `rounded-control border bg-surface px-3 py-2 text-sm ${ALERT_TONES[tone]}`;

// A bordered panel, for the rare thing that must stand apart (a one-time secret). Rows and
// dividers do the grouping everywhere else: do not nest panels or box a list.
export const panelClasses =
  "rounded-panel border border-line bg-surface p-4 text-primary";

// A table sits in the same bordered panel as the list rows, so its edges are rounded and padded.
export const tableScrollClasses =
  "overflow-x-auto rounded-panel border border-line bg-surface";
