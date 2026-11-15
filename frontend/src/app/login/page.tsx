import type { Metadata } from "next";
import LoginForm from "@/components/login-form";

export const metadata: Metadata = { title: "Sign in · Connecta" };

export default function LoginPage() {
  return (
    <main className="flex flex-1 items-center justify-center p-8">
      <div className="flex w-full max-w-sm flex-col gap-6">
        <div className="flex flex-col gap-1">
          <h1 className="text-3xl font-semibold tracking-tight">Connecta</h1>
          <p className="text-zinc-600 dark:text-zinc-400">
            Sign in to your inbox.
          </p>
        </div>
        <LoginForm />
      </div>
    </main>
  );
}
