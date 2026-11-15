import type { Metadata } from "next";
import OrgAcceptForm from "@/components/org-accept-form";
import { getCurrentUser } from "@/lib/session";

export const metadata: Metadata = { title: "Accept invitation · Connecta" };

export default async function AcceptInvitationPage({
  searchParams,
}: {
  searchParams: Promise<{ token?: string }>;
}) {
  await getCurrentUser("/invitations/accept");
  const { token } = await searchParams;

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 p-6 sm:p-8">
      <div className="flex flex-col gap-1">
        <h1 className="text-3xl font-semibold tracking-tight">
          Accept an invitation
        </h1>
        <p className="text-zinc-600 dark:text-zinc-400">
          Paste the token you were sent. It works only for the email address it
          was issued to.
        </p>
      </div>
      <OrgAcceptForm initialToken={typeof token === "string" ? token : ""} />
    </main>
  );
}
