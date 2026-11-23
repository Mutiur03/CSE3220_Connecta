import type { Metadata } from "next";
import Link from "next/link";
import type { ReactNode } from "react";
import { PackageIcon, PackageSearchIcon, PlusIcon } from "@/components/icons";
import OrgPagination from "@/components/org-pagination";
import ProductFilterBar from "@/components/product-filter-bar";
import ProductTable from "@/components/product-table";
import { buttonClasses } from "@/components/ui/classes";
import { listCategoryOptions, listProducts } from "@/lib/product-api";
import { firstImageSources } from "@/lib/product-thumbnails";
import { canManageProducts } from "@/lib/product-permissions";
import {
  parseProductFilters,
  hasProductFilters,
  productsHref,
} from "@/lib/product-validation";
import {
  ORG_PAGE_SIZE,
  pageCount,
  pageRange,
  parsePageParam,
} from "@/lib/org-pagination";
import { loadOrganization, loadSession, notFoundOnMissing } from "../data";

type RouteParams = Promise<{ id: string }>;
type SearchParams = Promise<Record<string, string | string[] | undefined>>;

const PAGE_KEY = "page";

export const metadata: Metadata = { title: "Products" };

export default async function ProductsPage({
  params,
  searchParams,
}: {
  params: RouteParams;
  searchParams: SearchParams;
}) {
  const { id } = await params;
  const query = await searchParams;
  const page = parsePageParam(query[PAGE_KEY]);
  const { accessToken } = await loadSession(id);
  const [organization, categories] = await Promise.all([
    loadOrganization(id),
    notFoundOnMissing(listCategoryOptions(accessToken, id)),
  ]);
  const filters = parseProductFilters(
    query,
    categories.map((category) => category.id),
  );
  const products = await notFoundOnMissing(
    listProducts(accessToken, id, filters, page, ORG_PAGE_SIZE),
  );
  // Bounded: one request per product on this page (at most ORG_PAGE_SIZE, 20).
  const thumbnails = await firstImageSources(accessToken, id, products.content);

  const listPath = `/organizations/${id}/products`;
  const newHref = `${listPath}/new`;
  const canManage = canManageProducts(organization.callerRole);
  const filtered = hasProductFilters(filters);
  const range = pageRange(page, products.size, products.totalElements);
  const count = pageCount(products.totalElements, products.size);
  const pageLink = (target: number) => productsHref(listPath, filters, target);
  const newButton = (
    <Link href={newHref} className={buttonClasses("primary")}>
      <PlusIcon />
      New product
    </Link>
  );

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
          <h1 className="text-display font-medium">Products</h1>
          <p className="text-secondary">
            What this organization sells, with its price and the stock on hand
          </p>
        </div>
        {canManage && newButton}
      </header>

      {products.totalElements === 0 && !filtered ? (
        <EmptyState
          icon={<PackageIcon />}
          title="No products yet"
          detail={
            canManage
              ? "Add the first product with its price and reorder level. Stock is recorded on its page afterwards."
              : "An admin of this organization adds products. They will be listed here."
          }
          action={canManage ? newButton : null}
        />
      ) : (
        <section
          aria-labelledby="products-heading"
          className="flex flex-col gap-4"
        >
          <h2 id="products-heading" className="sr-only">
            Product list
          </h2>
          <ProductFilterBar
            listPath={listPath}
            filters={filters}
            categories={categories}
          />
          {products.totalElements === 0 ? (
            <EmptyState
              icon={<PackageSearchIcon />}
              title="No products match these filters"
              detail="Search for another name or SKU, or widen the category and stock level."
              action={
                <Link href={listPath} className={buttonClasses("secondary")}>
                  Reset filters
                </Link>
              }
            />
          ) : (
            <div className="flex flex-col gap-3">
              <ProductTable
                orgId={id}
                products={products.content}
                thumbnails={thumbnails}
                canManage={canManage}
              />
              <div className="flex flex-wrap items-center justify-between gap-3">
                {range && (
                  <p className="figure text-caption text-secondary">
                    {range.from}–{range.to} of {products.totalElements} products
                  </p>
                )}
                <OrgPagination
                  label="Products pages"
                  page={page}
                  pageCount={count}
                  previousHref={page > 0 ? pageLink(page - 1) : undefined}
                  nextHref={page + 1 < count ? pageLink(page + 1) : undefined}
                />
              </div>
            </div>
          )}
        </section>
      )}
    </>
  );
}

/** A specific empty or no-match state: what is missing, why, and the one thing to do next. */
function EmptyState({
  icon,
  title,
  detail,
  action,
}: {
  icon: ReactNode;
  title: string;
  detail: string;
  action: ReactNode;
}) {
  return (
    <div className="flex flex-wrap items-start gap-x-4 gap-y-3 rounded-control border border-line p-4 sm:p-5">
      <span className="grid size-10 shrink-0 place-items-center rounded-control border border-line bg-app text-primary">
        {icon}
      </span>
      <div className="flex min-w-0 flex-1 basis-56 flex-col gap-1">
        <p className="font-medium">{title}</p>
        <p className="max-w-prose text-sm text-secondary">{detail}</p>
      </div>
      {action && (
        <div className="shrink-0 pl-14 sm:ml-auto sm:pl-0">{action}</div>
      )}
    </div>
  );
}
