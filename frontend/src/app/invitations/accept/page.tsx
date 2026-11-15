import type { Metadata } from "next";
import { Suspense } from "react";
import { InvitationFormSkeleton } from "../invitation-page-skeleton";
import AcceptInvitationContent from "./accept-invitation-content";

export const metadata: Metadata = { title: "Accept invitation" };

export default function AcceptInvitationPage({
  searchParams,
}: {
  searchParams: Promise<{ token?: string }>;
}) {
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 p-6 sm:p-8">
      <div className="flex flex-col gap-1">
        <h1 className="text-display font-medium tracking-tight">
          Accept an invitation
        </h1>
        <p className="text-ink-muted">
          Paste the token you were sent. It works only for the email address it
          was issued to.
        </p>
      </div>
      <Suspense fallback={<InvitationFormSkeleton />}>
        <AcceptInvitationContent searchParams={searchParams} />
      </Suspense>
    </main>
  );
}
