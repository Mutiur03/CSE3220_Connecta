import type { ReactNode } from "react";

// A template remounts on every navigation, so each page settles in with one short fade.
export default function AppTemplate({ children }: { children: ReactNode }) {
  return (
    <div className="enter flex flex-1 flex-col has-[[data-inbox]]:min-h-0">
      {children}
    </div>
  );
}
