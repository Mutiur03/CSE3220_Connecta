import type { Metadata } from "next";
import { redirect } from "next/navigation";
import SignupForm from "@/components/signup-form";
import { setup } from "@/lib/auth-actions";
import { isSetupRequired } from "@/lib/setup";
import AuthShell from "../auth-shell";

export const metadata: Metadata = { title: "Set up Connecta" };

// First run only: whoever opens this page on a fresh system becomes the owner of Connecta. Once
// an owner exists the page sends everyone on to signup.
export default function SetupPage() {
  return (
    <AuthShell
      title="Set up Connecta"
      description="Create the owner account. The owner runs the whole system and is not tied to one organization"
    >
      <SetupForm />
    </AuthShell>
  );
}

async function SetupForm() {
  if (!(await isSetupRequired())) redirect("/signup");
  return (
    <SignupForm
      action={setup}
      submitLabel="Create owner"
      pendingLabel="Creating owner…"
    />
  );
}
