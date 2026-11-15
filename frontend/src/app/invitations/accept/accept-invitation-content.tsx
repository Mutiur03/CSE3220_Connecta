import OrgAcceptForm from "@/components/org-accept-form";
import { getCurrentUser } from "@/lib/session";

/** The session check runs here, under Suspense, so the page heading can stream first. */
export default async function AcceptInvitationContent({
  searchParams,
}: {
  searchParams: Promise<{ token?: string }>;
}) {
  const { token } = await searchParams;
  const initialToken = typeof token === "string" ? token : "";
  // Keep the token in the return target, so an expired session refreshes back to this exact link.
  await getCurrentUser(
    typeof token === "string"
      ? `/invitations/accept?token=${encodeURIComponent(token)}`
      : "/invitations/accept",
  );

  return <OrgAcceptForm initialToken={initialToken} />;
}
