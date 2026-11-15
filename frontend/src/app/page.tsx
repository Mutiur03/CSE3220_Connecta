import BackendStatus from "./backend-status";

export default function Home() {
  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-2 p-8 text-center">
      <h1 className="text-4xl font-semibold tracking-tight">Connecta</h1>
      <p className="text-zinc-600 dark:text-zinc-400">
        Unified Instagram and Messenger inbox for your team.
      </p>
      <BackendStatus />
    </main>
  );
}
