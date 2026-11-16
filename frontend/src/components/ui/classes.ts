// Pure class-string builders for the base components. No React here, so the
// org-styles helpers and server components can share them.

export type ButtonTone = "primary" | "secondary" | "danger";
export type BadgeTone = "neutral" | "accent" | "success" | "danger";
export type AlertTone = "info" | "success" | "danger";

const BUTTON_BASE =
  "inline-flex items-center justify-center rounded-control px-3 py-1 text-sm font-medium transition-colors duration-150 ease-out disabled:opacity-60 motion-reduce:transition-none pointer-coarse:min-h-11";

const BUTTON_TONES: Record<ButtonTone, string> = {
  primary: "bg-accent text-accent-ink hover:bg-accent-hover",
  secondary: "border border-line-strong text-primary hover:bg-hover",
  danger: "border border-line-strong text-danger hover:bg-hover",
};

export const buttonClasses = (tone: ButtonTone = "secondary") =>
  `${BUTTON_BASE} ${BUTTON_TONES[tone]}`;

export const fieldClasses =
  "w-full min-w-0 rounded-control border border-line-strong bg-surface px-3 py-1 text-sm text-primary placeholder:text-secondary aria-invalid:border-danger pointer-coarse:min-h-11";

// Badges are full-radius tags on bg-hover; the tone only changes the text colour.
const BADGE_TONES: Record<BadgeTone, string> = {
  neutral: "text-secondary",
  accent: "text-accent",
  success: "text-success",
  danger: "text-danger",
};

export const badgeClasses = (tone: BadgeTone = "neutral") =>
  `inline-flex items-center rounded-full bg-hover px-2 py-0.5 text-caption font-medium ${BADGE_TONES[tone]}`;

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

export const tableScrollClasses = "overflow-x-auto rounded-control";
