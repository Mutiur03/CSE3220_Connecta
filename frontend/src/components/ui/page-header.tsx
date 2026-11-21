import type { ReactNode } from "react";

// The title block every page opens with: the page name, and one line of context. No full stop.
export function PageHeader({
  title,
  description,
  children,
}: {
  title: string;
  description?: string;
  children?: ReactNode;
}) {
  return (
    <header className="flex flex-wrap items-end justify-between gap-4">
      <div className="flex flex-col gap-1">
        <h1 className="text-display font-medium">{title}</h1>
        {description && <p className="text-secondary">{description}</p>}
      </div>
      {children}
    </header>
  );
}
