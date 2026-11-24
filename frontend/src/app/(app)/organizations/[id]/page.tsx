import type { Metadata } from "next";
import Link from "next/link";
import OrgActionButton from "@/components/org-action-button";
import OrgInvitations from "@/components/org-invitations";
import OrgMembersTable from "@/components/org-members-table";
import OrgPagination from "@/components/org-pagination";
import OrgRenameForm from "@/components/org-rename-form";
import OrgRoleBadge from "@/components/org-role-badge";
import { buttonClasses } from "@/components/ui/classes";
import { deleteOrganization, renameOrganization } from "@/lib/org-actions";
import { listInvitations, listMembers } from "@/lib/org-api";
import {
  assignableRoles,
  canDelete,
  canInvite,
  canRename,
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
      <RenameSection params={params} />
      <DeleteSection params={params} />
    </>
  );
}

async function OrganizationHeader({ params }: { params: RouteParams }) {
  const { id } = await params;
  const organization = await loadOrganization(id);

  return (
    <header className="flex flex-col gap-1">
      <Link
        href="/organizations"
        className="self-start text-sm text-secondary underline-offset-4 hover:underline"
      >
        All organizations
      </Link>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex min-w-0 flex-col gap-1">
          <h1 className="min-w-0 break-words text-display font-medium">
            {organization.name}
          </h1>
          <dl className="flex flex-wrap items-center gap-x-6 gap-y-1 text-sm text-secondary">
            <div className="flex items-center gap-2">
              <dt>Your role</dt>
              <dd>
                <OrgRoleBadge role={organization.callerRole} />
              </dd>
            </div>
            <div className="flex min-w-0 items-center gap-2">
              <dt>Slug</dt>
              <dd className="figure min-w-0 break-all text-primary">
                {organization.slug}
              </dd>
            </div>
          </dl>
        </div>
        <Link
          href={`/organizations/${id}/products`}
          className={buttonClasses("secondary")}
        >
          Product catalog
        </Link>
      </div>
    </header>
  );
}

/** A section title with an optional count beside it and one line of context under it. */
function SectionHeading({
  id,
  title,
  count,
  description,
}: {
  id: string;
  title: string;
  count?: number;
  description?: string;
}) {
  return (
    <div className="flex flex-col gap-1">
      <h2 id={id} className="flex items-baseline gap-2 text-lg font-medium">
        {title}
        {count !== undefined && (
          <span className="figure text-sm font-normal text-secondary">
            {count}
          </span>
        )}
      </h2>
      {description && <p className="text-secondary">{description}</p>}
    </div>
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
      <SectionHeading
        id="members-heading"
        title="Members"
        count={members.totalElements}
        description={
          canInvite(organization.callerRole)
            ? "An organization always keeps one admin, so the last admin cannot leave or become an agent."
            : "The people who answer this organization's conversations."
        }
      />
      {members.content.length === 0 ? (
        <p className="rounded-control border border-line px-4 py-4 text-secondary">
          No members on this page.{" "}
          <Link
            href={href(0)}
            className="text-primary underline underline-offset-4"
          >
            Go to the first page
          </Link>
        </p>
      ) : (
        <OrgMembersTable
          orgId={id}
          members={members.content}
          callerRole={organization.callerRole}
          callerUserId={user.id}
        />
      )}
      {range && count > 1 && (
        <p className="figure text-caption text-secondary">
          {range.from}–{range.to} of {members.totalElements}
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
      <SectionHeading
        id="invitations-heading"
        title="Invitations"
        count={invitations.totalElements}
        description="Invite someone by email. They join with the role you pick once they open the link."
      />
      <OrgInvitations
        orgId={id}
        invitations={invitations.content}
        roleOptions={assignableRoles(role)}
      />
      {range && count > 1 && (
        <p className="figure text-caption text-secondary">
          {range.from}–{range.to} of {invitations.totalElements}
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

async function RenameSection({ params }: { params: RouteParams }) {
  const { id } = await params;
  const organization = await loadOrganization(id);
  if (!canRename(organization.callerRole)) return null;

  return (
    <section aria-labelledby="rename-heading" className="flex flex-col gap-3">
      <SectionHeading
        id="rename-heading"
        title="Name"
        description="Shown in the sidebar and to everyone you invite."
      />
      <OrgRenameForm
        action={renameOrganization.bind(null, id)}
        name={organization.name}
      />
    </section>
  );
}

async function DeleteSection({ params }: { params: RouteParams }) {
  const { id } = await params;
  const organization = await loadOrganization(id);
  if (!canDelete(organization.callerRole)) return null;

  return (
    <section aria-labelledby="delete-heading" className="flex flex-col gap-3">
      <h2 id="delete-heading" className="text-lg font-medium">
        Danger zone
      </h2>
      <div className="flex flex-wrap items-center justify-between gap-x-6 gap-y-3 rounded-control border border-danger/40 px-4 py-3">
        <div className="flex min-w-0 flex-1 basis-64 flex-col gap-0.5">
          <p className="font-medium text-primary">Delete this organization</p>
          <p className="text-secondary">
            Removes {organization.name} with all its members and invitations.
            This cannot be undone.
          </p>
        </div>
        <OrgActionButton
          action={deleteOrganization.bind(null, id)}
          label="Delete organization"
          pendingLabel="Deleting…"
          confirm={`Delete ${organization.name}? Every member loses access and its invitations stop working. This cannot be undone.`}
          tone="danger"
        />
      </div>
    </section>
  );
}
