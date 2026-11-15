import type { Metadata } from "next";
import { Suspense } from "react";
import LoginForm from "@/components/login-form";
import { Alert } from "@/components/ui/alert";
import { UNAVAILABLE_REASON } from "@/lib/redirect";
import { LoginFormFallback } from "./login-form-fallback";

export const metadata: Metadata = { title: "Sign in · Connecta" };

type LoginSearchParams = {
  next?: string | string[];
  reason?: string | string[];
};

export default function LoginPage({
  searchParams,
}: {
  searchParams: Promise<LoginSearchParams>;
}) {
  return (
    <main className="flex flex-1 items-center justify-center p-8">
      <div className="flex w-full max-w-sm flex-col gap-6">
        <div className="flex flex-col gap-1">
          <h1 className="text-3xl font-semibold tracking-tight">Connecta</h1>
          <p className="text-ink-muted">Sign in to your inbox.</p>
        </div>
        {/* Reading searchParams makes the route dynamic, so it renders inside Suspense. */}
        <Suspense fallback={<LoginFormFallback />}>
          <LoginFormWithNext searchParams={searchParams} />
        </Suspense>
      </div>
    </main>
  );
}

async function LoginFormWithNext({
  searchParams,
}: {
  searchParams: Promise<LoginSearchParams>;
}) {
  const { next, reason } = await searchParams;
  return (
    <>
      {reason === UNAVAILABLE_REASON && (
        <Alert tone="danger">
          Connecta cannot reach the server right now. Try again in a few
          minutes.
        </Alert>
      )}
      <LoginForm next={typeof next === "string" ? next : ""} />
    </>
  );
}
