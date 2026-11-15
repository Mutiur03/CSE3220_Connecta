import type { Metadata } from "next";
import { Suspense } from "react";
import UserPanel, { UserPanelSkeleton } from "@/components/user-panel";

export const metadata: Metadata = { title: "Home" };

export default function Home() {
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-10 p-6 sm:p-8">
      <div className="flex flex-col gap-1">
        <h1 className="text-display font-semibold tracking-tight">Home</h1>
        <p className="text-ink-muted">Your account in Connecta.</p>
      </div>
      <Suspense fallback={<UserPanelSkeleton />}>
        <UserPanel />
      </Suspense>
    </main>
  );
}
