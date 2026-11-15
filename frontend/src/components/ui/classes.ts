// Pure class-string builders for the base components. No React here, so the
// org-styles helpers and server components can share them.

export type ButtonTone = "primary" | "secondary" | "danger";
export type BadgeTone = "neutral" | "accent" | "success" | "danger";
export type AlertTone = "info" | "success" | "danger";

const BUTTON_BASE =
  "inline-flex items-center justify-center rounded-control px-4 py-2 text-sm font-medium disabled:opacity-60 pointer-coarse:min-h-11";

const BUTTON_TONES: Record<ButtonTone, string> = {
  primary: "bg-accent text-accent-ink hover:bg-accent-hover",
  secondary: "border border-line-strong text-ink hover:bg-hover",
  danger: "border border-danger text-danger hover:bg-hover",
};

export const buttonClasses = (tone: ButtonTone = "secondary") =>
  `${BUTTON_BASE} ${BUTTON_TONES[tone]}`;

export const fieldClasses =
  "w-full min-w-0 rounded-control border border-line-strong bg-surface px-3 py-2 text-sm text-ink placeholder:text-ink-muted aria-invalid:border-danger pointer-coarse:min-h-11";

const BADGE_TONES: Record<BadgeTone, string> = {
  neutral: "border-line-strong text-ink-muted",
  accent: "border-accent text-accent",
  success: "border-success text-success",
  danger: "border-danger text-danger",
};

export const badgeClasses = (tone: BadgeTone = "neutral") =>
  `inline-flex items-center rounded-control border px-2 py-0.5 text-caption font-medium ${BADGE_TONES[tone]}`;

const ALERT_TONES: Record<AlertTone, string> = {
  info: "border-line-strong text-ink",
  success: "border-success text-success",
  danger: "border-danger text-danger",
};

export const alertClasses = (tone: AlertTone = "info") =>
  `rounded-control border bg-surface px-3 py-2 text-sm ${ALERT_TONES[tone]}`;

export const cardClasses =
  "rounded-panel border border-line bg-surface p-5 text-ink";

export const tableScrollClasses = "overflow-x-auto rounded-control";
