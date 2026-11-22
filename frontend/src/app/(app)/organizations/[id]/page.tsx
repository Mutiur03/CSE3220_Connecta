import type { Metadata } from "next";
import Link from "next/link";
import OrgActionButton from "@/components/org-action-button";
import OrgInvitations from "@/components/org-invitations";
import OrgMembersTable from "@/components/org-members-table";
import OrgPagination from "@/components/org-pagination";
import OrgRenameForm from "@/components/org-rename-form";
import { deleteOrganization, renameOrganization } from "@/lib/org-actions";
import { listInvitations, listMembers } from "@/lib/org-api";
import {
  assignableRoles,
  canDelete,
  canInvite,
  canRename,
  ROLE_LABELS,
} from "@/lib/org-permissions";
import {
  ORG_PAGE_SIZE,
  pageCount,
  pageHref,
  pageRange,
  parsePageParam,
} from "@/lib/org-pagination";
import {
  findOrganization,
  loadOrganization,
  loadSession,
  notFoundOnMissing,
} from "./data";

type RouteParams = Promise<{ id: string }>;
type SearchParams = Promise<Record<string, string | string[] | undefined>>;

const MEMBERS_KEY = "membersPage";
const INVITATIONS_KEY = "invitationsPage";

export async function generateMetadata({
  params,
}: {
  params: RouteParams;
}): Promise<Metadata> {
  const { id } = await params;
  const organization = await findOrganization(id);
  return { title: organization?.name ?? "Organization" };
}

// Each section streams behind its own boundary, so a slow list does not hold up the header.
export default function OrganizationPage({
  params,
  searchParams,
}: {
  params: RouteParams;
  searchParams: SearchParams;
}) {
  return (
    <>
      <OrganizationHeader params={params} />
      <MembersSection params={params} searchParams={searchParams} />
      <InvitationsSection params={params} searchParams={searchParams} />
      <DeleteSection params={params} />
    </>
  );
}

async function OrganizationHeader({ params }: { params: RouteParams }) {
  const { id } = await params;
  const organization = await loadOrganization(id);
  const role = organization.callerRole;

  return (
    <>
      <header className="flex flex-col gap-2">
        <Link
          href="/organizations"
          className="text-sm text-secondary underline-offset-4 hover:underline"
        >
          All organizations
        </Link>
        <h1 className="text-display font-medium">{organization.name}</h1>
        <p className="text-sm text-secondary">
          Slug <code>{organization.slug}</code> · Your role: {ROLE_LABELS[role]}
        </p>
        <Link
          href={`/organizations/${id}/products`}
          className="text-sm text-secondary underline-offset-4 hover:underline"
        >
          Product catalog
        </Link>
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
    </>
  );
}

async function MembersSection({
  params,
  searchParams,
}: {
  params: RouteParams;
  searchParams: SearchParams;
}) {
  const { id } = await params;
  const page = parsePageParam((await searchParams)[MEMBERS_KEY]);
  const { user, accessToken } = await loadSession(id);
  // The members and the organization are fetched together; a 404 on either shows the not-found page.
  const [organization, members] = await Promise.all([
    loadOrganization(id),
    notFoundOnMissing(listMembers(accessToken, id, page, ORG_PAGE_SIZE)),
  ]);
  const range = pageRange(page, members.size, members.totalElements);
  const count = pageCount(members.totalElements, members.size);
  const current = {
    [INVITATIONS_KEY]: parsePageParam((await searchParams)[INVITATIONS_KEY]),
  };
  const href = (target: number) =>
    pageHref(
      `/organizations/${id}`,
      { ...current, [MEMBERS_KEY]: page },
      MEMBERS_KEY,
      target,
    );

  return (
    <section aria-labelledby="members-heading" className="flex flex-col gap-3">
      <h2 id="members-heading" className="text-lg font-medium">
        Members
      </h2>
      <OrgMembersTable
        orgId={id}
        members={members.content}
        callerRole={organization.callerRole}
        callerUserId={user.id}
      />
      {range && (
        <p className="text-sm text-secondary tabular-nums">
          Showing {range.from}–{range.to} of {members.totalElements} members
        </p>
      )}
      <OrgPagination
        label="Members pages"
        page={page}
        pageCount={count}
        previousHref={page > 0 ? href(page - 1) : undefined}
        nextHref={page + 1 < count ? href(page + 1) : undefined}
      />
    </section>
  );
}

async function InvitationsSection({
  params,
  searchParams,
}: {
  params: RouteParams;
  searchParams: SearchParams;
}) {
  const { id } = await params;
  const organization = await loadOrganization(id);
  const role = organization.callerRole;
  if (!canInvite(role)) return null;

  const page = parsePageParam((await searchParams)[INVITATIONS_KEY]);
  const { accessToken } = await loadSession(id);
  const invitations = await notFoundOnMissing(
    listInvitations(accessToken, id, page, ORG_PAGE_SIZE),
  );
  const range = pageRange(page, invitations.size, invitations.totalElements);
  const count = pageCount(invitations.totalElements, invitations.size);
  const current = {
    [MEMBERS_KEY]: parsePageParam((await searchParams)[MEMBERS_KEY]),
  };
  const href = (target: number) =>
    pageHref(
      `/organizations/${id}`,
      { ...current, [INVITATIONS_KEY]: page },
      INVITATIONS_KEY,
      target,
    );

  return (
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
      {range && (
        <p className="text-sm text-secondary tabular-nums">
          Showing {range.from}–{range.to} of {invitations.totalElements}{" "}
          invitations
        </p>
      )}
      <OrgPagination
        label="Invitations pages"
        page={page}
        pageCount={count}
        previousHref={page > 0 ? href(page - 1) : undefined}
        nextHref={page + 1 < count ? href(page + 1) : undefined}
      />
    </section>
  );
}

async function DeleteSection({ params }: { params: RouteParams }) {
  const { id } = await params;
  const organization = await loadOrganization(id);
  if (!canDelete(organization.callerRole)) return null;

  return (
    <section
      aria-labelledby="delete-heading"
      className="flex flex-col gap-3 border-t border-line pt-6"
    >
      <h2 id="delete-heading" className="text-lg font-medium">
        Delete organization
      </h2>
      <p className="text-sm text-secondary">
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
  );
}
