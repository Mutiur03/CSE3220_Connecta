import type { Metadata } from "next";
import PasswordChangeForm from "@/components/password-change-form";
import { LockIcon, MonitorSmartphoneIcon, UserIcon } from "@/components/icons";
import SessionList from "@/components/session-list";
import SettingsSection from "@/components/settings-section";
import { Alert } from "@/components/ui/alert";
import { PageHeader } from "@/components/ui/page-header";
import { getAccessToken, getCurrentUser } from "@/lib/session";
import { listSessions } from "@/lib/session-api";

export const metadata: Metadata = {
  title: { absolute: "Settings · Connecta" },
};

const initials = (name: string) =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => word[0].toUpperCase())
    .join("");

export default async function SettingsPage() {
  const user = await getCurrentUser(); // refreshes an expired access token before it is read
  const accessToken = await getAccessToken("/settings");
  const sessions = await listSessions(accessToken).catch(() => null);

  return (
    <main className="mx-auto flex w-full max-w-4xl flex-1 flex-col gap-8 p-6 sm:p-8">
      <PageHeader
        title="Settings"
        description="Your profile, password and signed-in devices"
      />
      <SettingsSection
        id="profile-heading"
        icon={<UserIcon />}
        title="Profile"
        description="How you appear to your team"
      >
        <div className="flex items-center gap-4">
          <span
            aria-hidden="true"
            className="grid size-12 shrink-0 place-items-center rounded-full bg-selected text-lg font-medium"
          >
            {initials(user.fullName)}
          </span>
          <dl className="flex min-w-0 flex-col gap-0.5">
            <dt className="sr-only">Name</dt>
            <dd className="font-medium">{user.fullName}</dd>
            <dt className="sr-only">Email</dt>
            <dd className="break-all text-secondary">{user.email}</dd>
          </dl>
        </div>
      </SettingsSection>
      <SettingsSection
        id="password-heading"
        icon={<LockIcon />}
        title="Password"
        description="Use a long one you do not use anywhere else"
      >
        <PasswordChangeForm email={user.email} />
      </SettingsSection>
      <SettingsSection
        id="sessions-heading"
        icon={<MonitorSmartphoneIcon />}
        title="Signed-in devices"
        description="Sign out any device you do not recognise"
      >
        {sessions ? (
          <SessionList sessions={sessions.content} now={new Date()} />
        ) : (
          <Alert tone="danger">
            Could not load your devices. Reload the page to try again.
          </Alert>
        )}
      </SettingsSection>
    </main>
  );
}
