import type { ReactNode } from "react";

// One width and gutter for every organization page. The page files render their content only.
export default function OrganizationsLayout({
  children,
}: {
  children: ReactNode;
}) {
  return (
    <main className="mx-auto flex w-full max-w-4xl flex-1 flex-col gap-10 p-6 sm:p-8">
      {children}
    </main>
  );
}
