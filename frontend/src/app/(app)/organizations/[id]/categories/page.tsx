import type { Metadata } from "next";
import Link from "next/link";
import OrgPagination from "@/components/org-pagination";
import CategoryTable from "@/components/category-table";
import { buttonClasses } from "@/components/ui/classes";
import { listCategories } from "@/lib/category-api";
import { canManageCategories } from "@/lib/category-permissions";
import {
  ORG_PAGE_SIZE,
  pageCount,
  pageHref,
  pageRange,
  parsePageParam,
} from "@/lib/org-pagination";
import {
  loadOrganization,
  loadSession,
  notFoundOnMissing,
} from "../data";

type RouteParams = Promise<{ id: string }>;
type SearchParams = Promise<Record<string, string | string[] | undefined>>;

const PAGE_KEY = "page";

export const metadata: Metadata = { title: "Categories" };

export default async function CategoriesPage({
  params,
  searchParams,
}: {
  params: RouteParams;
  searchParams: SearchParams;
}) {
  const { id } = await params;
  const page = parsePageParam((await searchParams)[PAGE_KEY]);
  const { accessToken } = await loadSession(id);
  const [organization, categories] = await Promise.all([
    loadOrganization(id),
    notFoundOnMissing(listCategories(accessToken, id, page, ORG_PAGE_SIZE)),
  ]);
  const canManage = canManageCategories(organization.callerRole);
  const range = pageRange(page, categories.size, categories.totalElements);
  const count = pageCount(categories.totalElements, categories.size);
  const href = (target: number) =>
    pageHref(`/organizations/${id}/categories`, { [PAGE_KEY]: page }, PAGE_KEY, target);

  return (
    <>
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex flex-col gap-1">
          <Link
            href={`/organizations/${id}`}
            className="text-sm text-secondary underline-offset-4 hover:underline"
          >
            {organization.name}
          </Link>
          <h1 className="text-display font-medium">Categories</h1>
        </div>
        {canManage && (
          <Link
            href={`/organizations/${id}/categories/new`}
            className={buttonClasses("primary")}
          >
            New category
          </Link>
        )}
      </header>

      {categories.totalElements === 0 ? (
        <div className="flex flex-col items-start gap-2 border-t border-line-subtle pt-8">
          <p className="font-medium">No categories yet</p>
          <p className="text-secondary">
            {canManage
              ? "Add the first category to group this organization's products."
              : "An admin of this organization adds categories here."}
          </p>
          {canManage && (
            <Link
              href={`/organizations/${id}/categories/new`}
              className={buttonClasses("primary")}
            >
              New category
            </Link>
          )}
        </div>
      ) : (
        <section aria-labelledby="categories-heading" className="flex flex-col gap-3">
          <h2 id="categories-heading" className="sr-only">
            Category list
          </h2>
          <CategoryTable orgId={id} categories={categories.content} canManage={canManage} />
          {range && (
            <p className="text-sm text-secondary tabular-nums">
              Showing {range.from}–{range.to} of {categories.totalElements} categories
            </p>
          )}
          <OrgPagination
            label="Categories pages"
            page={page}
            pageCount={count}
            previousHref={page > 0 ? href(page - 1) : undefined}
            nextHref={page + 1 < count ? href(page + 1) : undefined}
          />
        </section>
      )}
    </>
  );
}
