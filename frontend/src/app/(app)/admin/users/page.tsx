import type { Metadata } from "next";
import { notFound } from "next/navigation";
import OrgPagination from "@/components/org-pagination";
import { Alert } from "@/components/ui/alert";
import { PageHeader } from "@/components/ui/page-header";
import UserTable from "@/components/user-table";
import { ApiError } from "@/lib/api";
import { actionErrorMessage } from "@/lib/org-errors";
import { isSystemOwner } from "@/lib/org-permissions";
import {
  ORG_PAGE_SIZE,
  pageCount,
  pageHref,
  parsePageParam,
} from "@/lib/org-pagination";
import {
  getAccessToken,
  getCurrentUser,
  redirectOnUnauthorized,
} from "@/lib/session";
import { listUsers } from "@/lib/user-api";

export const metadata: Metadata = { title: "Users" };

const PATH = "/admin/users";

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

// The system owner's view: every account, with a way to disable and re-enable it.
export default function UsersPage({
  searchParams,
}: {
  searchParams: SearchParams;
}) {
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col gap-8 p-6 sm:p-8">
      <PageHeader title="Users" description="Every account in Connecta" />
      <Users searchParams={searchParams} />
    </main>
  );
}

async function Users({ searchParams }: { searchParams: SearchParams }) {
  const user = await getCurrentUser(PATH);
  // Other roles get the same page as an unknown address, so the route is not advertised.
  if (!isSystemOwner(user.roles)) notFound();

  const accessToken = await getAccessToken(PATH);
  const page = parsePageParam((await searchParams).page);
  let result;
  try {
    result = await listUsers(accessToken, page, ORG_PAGE_SIZE);
  } catch (error) {
    if (error instanceof ApiError && error.status === 401)
      await redirectOnUnauthorized(error);
    return <Alert tone="danger">{actionErrorMessage(error)}</Alert>;
  }
  const count = pageCount(result.totalElements, result.size);

  return (
    <section aria-label="Users" className="flex flex-col gap-3">
      {result.content.length === 0 ? (
        <p className="text-secondary">No users yet.</p>
      ) : (
        <UserTable users={result.content} callerUserId={user.id} />
      )}
      <OrgPagination
        label="Users pages"
        page={page}
        pageCount={count}
        previousHref={
          page > 0 ? pageHref(PATH, { page }, "page", page - 1) : undefined
        }
        nextHref={
          page + 1 < count
            ? pageHref(PATH, { page }, "page", page + 1)
            : undefined
        }
      />
    </section>
  );
}
