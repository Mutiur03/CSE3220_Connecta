import type { Metadata } from "next";
import SessionList from "@/components/session-list";
import { Alert } from "@/components/ui/alert";
import { PageHeader } from "@/components/ui/page-header";
import { getAccessToken, getCurrentUser } from "@/lib/session";
import { listSessions } from "@/lib/session-api";

export const metadata: Metadata = {
  title: { absolute: "Signed-in devices · Connecta" },
};

export default async function SessionsPage() {
  await getCurrentUser(); // refreshes an expired access token before it is read
  const accessToken = await getAccessToken("/account/sessions");
  const sessions = await listSessions(accessToken).catch(() => null);

  return (
    <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col gap-8 p-6 sm:p-8">
      <PageHeader
        title="Signed-in devices"
        description="Sign out any device you no longer use"
      />
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
