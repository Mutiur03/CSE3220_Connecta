import type { Metadata } from "next";
import AuthShell from "../../auth-shell";
import AcceptInvitationContent from "./accept-invitation-content";

export const metadata: Metadata = { title: "Accept invitation" };

// Open to signed-out visitors: an invited person has no account yet.
export default function AcceptInvitationPage({
  searchParams,
}: {
  searchParams: Promise<{ token?: string }>;
}) {
  return (
    <AuthShell
      title="Accept your invitation"
      description="Join your team's organization on Connecta"
    >
      <AcceptInvitationContent searchParams={searchParams} />
    </AuthShell>
  );
}
