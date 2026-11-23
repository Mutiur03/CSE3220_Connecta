import type { Metadata } from "next";
import Link from "next/link";
import OrgPagination from "@/components/org-pagination";
import CategoryTable from "@/components/category-table";
import { PlusIcon, TagIcon } from "@/components/icons";
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
import { loadOrganization, loadSession, notFoundOnMissing } from "../data";

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
    pageHref(
      `/organizations/${id}/categories`,
      { [PAGE_KEY]: page },
      PAGE_KEY,
      target,
    );

  const newHref = `/organizations/${id}/categories/new`;
  const empty = categories.totalElements === 0;

  return (
    <>
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex flex-col gap-1">
          <Link
            href={`/organizations/${id}`}
            className="self-start text-sm text-secondary underline-offset-4 hover:underline"
          >
            {organization.name}
          </Link>
          <div className="flex items-baseline gap-2">
            <h1 className="text-display font-medium">Categories</h1>
            {!empty && (
              <span className="figure text-caption text-secondary">
                {categories.totalElements}
              </span>
            )}
          </div>
          <p className="text-secondary">
            Group products by kind so the team finds the right one when a
            customer asks.
          </p>
        </div>
        {/* The empty state carries the only primary action when there is nothing to list. */}
        {canManage && !empty && <NewCategoryLink href={newHref} />}
      </header>

      {empty ? (
        <section
          aria-labelledby="categories-empty-heading"
          className="flex flex-col items-start gap-4 rounded-control border border-line bg-surface p-5"
        >
          <span className="grid size-10 place-items-center rounded-control border border-line bg-app text-primary">
            <TagIcon />
          </span>
          <div className="flex flex-col gap-1">
            <h2 id="categories-empty-heading" className="text-lg font-medium">
              No categories yet
            </h2>
            <p className="max-w-prose text-secondary">
              {canManage
                ? "Create a category such as Beverages or Skincare, then pick it when you add or edit a product."
                : "An admin of this organization creates categories. They appear here once they exist."}
            </p>
          </div>
          {canManage && <NewCategoryLink href={newHref} />}
        </section>
      ) : categories.content.length === 0 ? (
        <section className="flex flex-col items-start gap-3 rounded-control border border-line bg-surface p-5">
          <p className="text-secondary">
            This page is past the end of the list.
          </p>
          <Link href={href(0)} className={buttonClasses("secondary")}>
            Go to the first page
          </Link>
        </section>
      ) : (
        <section
          aria-labelledby="categories-heading"
          className="flex flex-col gap-3"
        >
          <h2 id="categories-heading" className="sr-only">
            Category list
          </h2>
          <CategoryTable
            orgId={id}
            categories={categories.content}
            canManage={canManage}
          />
          {range && count > 1 && (
            <p className="figure text-caption text-secondary">
              {range.from}–{range.to} of {categories.totalElements}
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

function NewCategoryLink({ href }: { href: string }) {
  return (
    <Link href={href} className={`${buttonClasses("primary")} [&_svg]:size-4`}>
      <PlusIcon />
      New category
    </Link>
  );
}
