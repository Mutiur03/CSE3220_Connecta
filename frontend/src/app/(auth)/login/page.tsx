import type { Metadata } from "next";
import { redirect } from "next/navigation";
import LoginForm from "@/components/login-form";
import { Alert } from "@/components/ui/alert";
import { UNAVAILABLE_REASON } from "@/lib/redirect";
import { isSetupRequired } from "@/lib/setup";
import AuthShell, { AuthSwitch } from "../auth-shell";

export const metadata: Metadata = { title: "Sign in" };

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
    <AuthShell
      title="Sign in to Connecta"
      description="Pick up your team's conversations where you left them"
    >
      <LoginFormWithNext searchParams={searchParams} />
    </AuthShell>
  );
}

async function LoginFormWithNext({
  searchParams,
}: {
  searchParams: Promise<LoginSearchParams>;
}) {
  // A system with no owner yet must be set up first.
  if (await isSetupRequired()) redirect("/setup");
  const { next, reason } = await searchParams;
  const target = typeof next === "string" ? next : "";
  return (
    <div className="flex flex-col gap-4">
      {reason === UNAVAILABLE_REASON && (
        <Alert tone="danger">
          Connecta cannot reach the server right now. Try again in a few
          minutes.
        </Alert>
      )}
      <LoginForm next={target} />
      <AuthSwitch
        question="New to Connecta?"
        href={target ? `/signup?next=${encodeURIComponent(target)}` : "/signup"}
        action="Create an account"
      />
    </div>
  );
}
