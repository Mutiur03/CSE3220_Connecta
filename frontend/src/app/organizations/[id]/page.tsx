import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import OrgActionButton from "@/components/org-action-button";
import OrgInvitations from "@/components/org-invitations";
import OrgMembersTable from "@/components/org-members-table";
import OrgRenameForm from "@/components/org-rename-form";
import OrgRoleBadge from "@/components/org-role-badge";
import { deleteOrganization, renameOrganization } from "@/lib/org-actions";
import { ApiError } from "@/lib/api";
import { getOrganization, listInvitations, listMembers } from "@/lib/org-api";
import {
  assignableRoles,
  canDelete,
  canInvite,
  canRename,
  ROLE_LABELS,
} from "@/lib/org-permissions";
import { isUuid } from "@/lib/org-validation";
import { getAccessToken, getCurrentUser } from "@/lib/session";

export const metadata: Metadata = { title: "Organization · Connecta" };

export default async function OrganizationPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  // The id goes into a redirect path, so only a UUID is accepted here.
  if (!isUuid(id)) notFound();

  const path = `/organizations/${id}`;
  const user = await getCurrentUser(path);
  const accessToken = await getAccessToken(path);
  // Another tenant's organization and a missing one both answer 404, so both show the same page.
  const organization = await getOrganization(accessToken, id).catch(
    (error: unknown) => {
      if (error instanceof ApiError && error.status === 404) notFound();
      throw error;
    },
  );

  const role = organization.callerRole;
  const [members, invitations] = await Promise.all([
    listMembers(accessToken, id),
    canInvite(role) ? listInvitations(accessToken, id) : undefined,
  ]);

  return (
    <main className="mx-auto flex w-full max-w-4xl flex-1 flex-col gap-10 p-6 sm:p-8">
      <header className="flex flex-col gap-2">
        <Link
          href="/organizations"
          className="text-sm text-zinc-500 underline-offset-4 hover:underline"
        >
          All organizations
        </Link>
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-3xl font-semibold tracking-tight">
            {organization.name}
          </h1>
          <OrgRoleBadge role={role} />
        </div>
        <p className="text-sm text-zinc-500">
          Slug: <code>{organization.slug}</code> · You are{" "}
          {ROLE_LABELS[role].toLowerCase()}
        </p>
      </header>

      {canRename(role) && (
        <section
          aria-labelledby="rename-heading"
          className="flex flex-col gap-3"
        >
          <h2 id="rename-heading" className="text-lg font-medium">
            Name
          </h2>
          <OrgRenameForm
            action={renameOrganization.bind(null, id)}
            name={organization.name}
          />
        </section>
      )}

      <section
        aria-labelledby="members-heading"
        className="flex flex-col gap-3"
      >
        <h2 id="members-heading" className="text-lg font-medium">
          Members
        </h2>
        <OrgMembersTable
          orgId={id}
          members={members.content}
          callerRole={role}
          callerUserId={user.id}
        />
        {members.totalElements > members.content.length && (
          <p className="text-sm text-zinc-500">
            Showing {members.content.length} of {members.totalElements} members.
          </p>
        )}
      </section>

      {invitations && (
        <section
          aria-labelledby="invitations-heading"
          className="flex flex-col gap-3"
        >
          <h2 id="invitations-heading" className="text-lg font-medium">
            Invitations
          </h2>
          <OrgInvitations
            orgId={id}
            invitations={invitations.content}
            roleOptions={assignableRoles(role)}
          />
        </section>
      )}

      {canDelete(role) && (
        <section
          aria-labelledby="delete-heading"
          className="flex flex-col gap-3 border-t border-zinc-200 pt-6 dark:border-zinc-800"
        >
          <h2 id="delete-heading" className="text-lg font-medium">
            Delete organization
          </h2>
          <p className="text-sm text-zinc-500">
            Deletes the organization and all its memberships and invitations.
          </p>
          <OrgActionButton
            action={deleteOrganization.bind(null, id)}
            label="Delete organization"
            pendingLabel="Deleting…"
            confirm="Delete this organization? This cannot be undone."
            tone="danger"
          />
        </section>
      )}
    </main>
  );
}
