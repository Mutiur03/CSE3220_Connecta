import type { Metadata } from "next";
import { redirect } from "next/navigation";
import SignupForm from "@/components/signup-form";
import { signup } from "@/lib/auth-actions";
import { isSetupRequired } from "@/lib/setup";
import AuthShell, { AuthSwitch } from "../auth-shell";

export const metadata: Metadata = { title: "Create account" };

type SignupSearchParams = Promise<{ next?: string | string[] }>;

// Signup makes an admin. Agents join only through an invitation, so there is no role to pick.
export default function SignupPage({
  searchParams,
}: {
  searchParams: SignupSearchParams;
}) {
  return (
    <AuthShell
      title="Create your admin account"
      description="Admins create organizations and invite their team by email"
    >
      <SignupFormWithNext searchParams={searchParams} />
    </AuthShell>
  );
}

async function SignupFormWithNext({
  searchParams,
}: {
  searchParams: SignupSearchParams;
}) {
  // A system with no owner yet must be set up first.
  if (await isSetupRequired()) redirect("/setup");
  const { next } = await searchParams;
  const target = typeof next === "string" ? next : "";
  return (
    <div className="flex flex-col gap-4">
      <SignupForm
        action={signup}
        submitLabel="Create account"
        pendingLabel="Creating account…"
        next={target}
      />
      <AuthSwitch
        question="Already have an account?"
        href={target ? `/login?next=${encodeURIComponent(target)}` : "/login"}
        action="Sign in"
      />
    </div>
  );
}
