import { signedInUser } from "@/components/app-rail";
import AcceptNewForm from "@/components/accept-new-form";
import InvitationJoinForm from "@/components/invitation-join-form";
import OrgAcceptForm from "@/components/org-accept-form";
import { Alert } from "@/components/ui/alert";
import { buttonClasses } from "@/components/ui/classes";
import { ApiError, previewInvitation, type InvitationPreview } from "@/lib/api";
import { logout } from "@/lib/auth-actions";
import { roleLabel } from "@/lib/org-permissions";
import { AuthSwitch } from "../../auth-shell";

const PATH = "/invitations/accept";

type Preview =
  | { kind: "found"; invitation: InvitationPreview }
  | { kind: "invalid" }
  | { kind: "unavailable" };

async function loadPreview(token: string): Promise<Preview> {
  try {
    return { kind: "found", invitation: await previewInvitation(token) };
  } catch (error) {
    if (error instanceof ApiError && error.status < 500) {
      return { kind: "invalid" };
    }
    return { kind: "unavailable" };
  }
}

const Summary = ({ invitation }: { invitation: InvitationPreview }) => (
  <p className="text-secondary">
    {invitation.inviterName} invited <strong>{invitation.email}</strong> to join{" "}
    <strong>{invitation.organizationName}</strong> as{" "}
    {roleLabel(invitation.role).toLowerCase()}.
  </p>
);

/**
 * What the visitor sees depends on the token and on who they are: someone with no account creates
 * one here, someone with an account signs in first, and a signed-in match just joins.
 */
export default async function AcceptInvitationContent({
  searchParams,
}: {
  searchParams: Promise<{ token?: string }>;
}) {
  const { token } = await searchParams;
  const user = await signedInUser();

  if (typeof token !== "string" || !token) {
    // No link to open: a signed-in person can still paste the token they were given.
    return user ? (
      <OrgAcceptForm initialToken="" />
    ) : (
      <>
        <Alert tone="info">
          Open the link from your invitation email to join an organization.
        </Alert>
        <AuthSwitch
          question="Already a member?"
          href="/login"
          action="Sign in"
        />
      </>
    );
  }

  const preview = await loadPreview(token);
  if (preview.kind === "unavailable") {
    return (
      <Alert tone="danger">
        Connecta cannot reach the server right now. Try again in a few minutes.
      </Alert>
    );
  }
  if (preview.kind === "invalid") {
    return (
      <>
        <Alert tone="danger">
          This invitation is not valid. It may have expired, been withdrawn or
          already been used. Ask your admin to send a new one.
        </Alert>
        <AuthSwitch
          question="Have an account?"
          href="/login"
          action="Sign in"
        />
      </>
    );
  }

  const { invitation } = preview;
  const returnTo = `${PATH}?token=${encodeURIComponent(token)}`;

  if (user) {
    if (user.email.toLowerCase() !== invitation.email.toLowerCase()) {
      return (
        <>
          <Summary invitation={invitation} />
          <Alert tone="danger">
            You are signed in as {user.email}. Sign out and open this link again
            to use {invitation.email}.
          </Alert>
          <form action={logout}>
            <button type="submit" className={buttonClasses("secondary")}>
              Sign out
            </button>
          </form>
        </>
      );
    }
    return (
      <>
        <Summary invitation={invitation} />
        <InvitationJoinForm
          token={token}
          organization={invitation.organizationName}
        />
      </>
    );
  }

  if (invitation.accountExists) {
    return (
      <>
        <Summary invitation={invitation} />
        <a
          href={`/login?next=${encodeURIComponent(returnTo)}`}
          className={`${buttonClasses("primary")} min-h-11`}
        >
          Sign in to accept
        </a>
      </>
    );
  }

  return (
    <>
      <Summary invitation={invitation} />
      <AcceptNewForm token={token} email={invitation.email} />
    </>
  );
}
