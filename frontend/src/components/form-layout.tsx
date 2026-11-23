import type { ReactNode } from "react";

// The settings-style form every create and edit page shares: one panel, sections split by
// dividers (title and a line of context on the left from lg up, fields on the right), and a
// footer row inside the panel for the action-level alert and the buttons.

export function FormPanel({ children }: { children: ReactNode }) {
  return (
    <div className="flex max-w-4xl flex-col divide-y divide-line rounded-panel border border-line bg-surface">
      {children}
    </div>
  );
}

export function FormSection({
  title,
  description,
  children,
}: {
  title: string;
  description?: string;
  children: ReactNode;
}) {
  // The visible heading names the group, so the section is reachable by heading and by group.
  const headingId = `form-section-${title.toLowerCase().replace(/[^a-z0-9]+/g, "-")}`;
  return (
    <fieldset aria-labelledby={headingId} className="min-w-0 p-4 sm:p-6">
      <div className="grid gap-4 lg:grid-cols-[13rem_minmax(0,1fr)] lg:gap-8">
        <div className="flex flex-col gap-1">
          <h2 id={headingId} className="text-lg font-medium text-primary">
            {title}
          </h2>
          {description && (
            <p className="text-sm text-secondary">{description}</p>
          )}
        </div>
        <div className="flex min-w-0 flex-col gap-4">{children}</div>
      </div>
    </fieldset>
  );
}

/** Alert on the left (it only ever holds the action's own failure), buttons on the right. */
export function FormFooter({
  alert,
  children,
}: {
  alert?: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-3 rounded-b-panel bg-app/60 px-4 py-3 sm:flex-row sm:items-center sm:px-6">
      <div className="min-w-0 flex-1">{alert}</div>
      <div className="flex flex-wrap items-center gap-2 sm:justify-end">
        {children}
      </div>
    </div>
  );
}
