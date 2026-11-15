import { Suspense } from "react";
import BackendStatus from "@/components/backend-status";
import UserPanel from "@/components/user-panel";

export default function Home() {
  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-6 p-8 text-center">
      <div className="flex flex-col gap-2">
        <h1 className="text-4xl font-semibold tracking-tight">Connecta</h1>
        <p className="text-zinc-600 dark:text-zinc-400">
          Unified Instagram and Messenger inbox for your team.
        </p>
      </div>
      <Suspense fallback={<p className="text-zinc-500">Loading…</p>}>
        <UserPanel />
      </Suspense>
      <BackendStatus />
    </main>
  );
}
