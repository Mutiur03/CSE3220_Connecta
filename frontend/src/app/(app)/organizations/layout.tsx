import type { ReactNode } from "react";

// One width and gutter for every organization page. The page files render their content only.
// The inbox (marked data-inbox) is the exception: its panes run edge to edge, so the width and gutter drop.
export default function OrganizationsLayout({
  children,
}: {
  children: ReactNode;
}) {
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col gap-8 p-6 sm:p-8 has-[[data-inbox]]:min-h-0 has-[[data-inbox]]:max-w-none has-[[data-inbox]]:gap-0 has-[[data-inbox]]:p-0">
      {children}
    </main>
  );
}
