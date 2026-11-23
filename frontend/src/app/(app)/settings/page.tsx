import type { Metadata } from "next";
import PasswordChangeForm from "@/components/password-change-form";
import SessionList from "@/components/session-list";
import { Alert } from "@/components/ui/alert";
import { PageHeader } from "@/components/ui/page-header";
import { getAccessToken, getCurrentUser } from "@/lib/session";
import { listSessions } from "@/lib/session-api";

export const metadata: Metadata = {
  title: { absolute: "Settings · Connecta" },
};

export default async function SettingsPage() {
  const user = await getCurrentUser(); // refreshes an expired access token before it is read
  const accessToken = await getAccessToken("/settings");
  const sessions = await listSessions(accessToken).catch(() => null);

  return (
    <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col gap-8 p-6 sm:p-8">
      <PageHeader
        title="Settings"
        description="Your account and the devices signed in to it"
      />
      <section
        aria-labelledby="account-heading"
        className="flex flex-col gap-4"
      >
        <h2 id="account-heading" className="text-lg font-medium">
          Account
        </h2>
        <dl className="divide-y divide-line-subtle border-y border-line">
          <div className="flex gap-4 py-3">
            <dt className="w-20 shrink-0 text-secondary">Name</dt>
            <dd className="font-medium">{user.fullName}</dd>
          </div>
          <div className="flex gap-4 py-3">
            <dt className="w-20 shrink-0 text-secondary">Email</dt>
            <dd className="min-w-0 break-all">{user.email}</dd>
          </div>
        </dl>
      </section>
      <section
        aria-labelledby="password-heading"
        className="flex flex-col gap-4"
      >
        <h2 id="password-heading" className="text-lg font-medium">
          Password
        </h2>
        <PasswordChangeForm />
      </section>
      {sessions ? (
        <SessionList sessions={sessions.content} />
      ) : (
        <Alert tone="danger">
          Could not load your devices. Reload the page to try again.
        </Alert>
      )}
    </main>
  );
}
