import type { ReactNode } from "react";
import AppRail from "@/components/app-rail";

// The signed-in shell: the rail plus the page. Sign-in lives in (auth) and has no rail.
// The inbox (data-inbox) pins the shell to the viewport so its panes scroll on their own.
export default function AppLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col min-[800px]:flex-row has-[[data-inbox]]:h-dvh">
      <AppRail />
      <div className="flex min-w-0 flex-1 flex-col has-[[data-inbox]]:min-h-0">
        {children}
      </div>
    </div>
  );
}
