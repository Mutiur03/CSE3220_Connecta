import type { ReactNode } from "react";

/** One settings group: icon, title and a line of context on the left, the controls on the right. */
export default function SettingsSection({
  id,
  icon,
  title,
  description,
  action,
  children,
}: {
  id: string;
  icon: ReactNode;
  title: string;
  description: string;
  action?: ReactNode;
  children: ReactNode;
}) {
  return (
    <section
      aria-labelledby={id}
      className="grid gap-4 border-t border-line pt-8 lg:grid-cols-[15rem_minmax(0,1fr)] lg:gap-10"
    >
      <div className="flex flex-col gap-1">
        <h2 id={id} className="flex items-center gap-2 text-lg font-medium">
          <span className="text-secondary">{icon}</span>
          {title}
        </h2>
        <p className="text-secondary">{description}</p>
      </div>
      <div className="flex min-w-0 flex-col gap-4">
        {action}
        {children}
      </div>
    </section>
  );
}
