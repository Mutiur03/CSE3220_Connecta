import type { Metadata } from "next";
import Link from "next/link";
import OrgPagination from "@/components/org-pagination";
import ProductFilterBar from "@/components/product-filter-bar";
import ProductTable from "@/components/product-table";
import { buttonClasses } from "@/components/ui/classes";
import { listCategoryOptions, listProducts } from "@/lib/product-api";
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
import {
  loadOrganization,
  loadSession,
  notFoundOnMissing,
} from "../data";

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

  const listPath = `/organizations/${id}/products`;
  const canManage = canManageProducts(organization.callerRole);
  const filtered = hasProductFilters(filters);
  const range = pageRange(page, products.size, products.totalElements);
  const count = pageCount(products.totalElements, products.size);
  const pageLink = (target: number) => productsHref(listPath, filters, target);

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
          <h1 className="text-display font-medium">Products</h1>
        </div>
        {canManage && (
          <Link
            href={`/organizations/${id}/products/new`}
            className={buttonClasses("primary")}
          >
            New product
          </Link>
        )}
      </header>

      {products.totalElements === 0 && !filtered ? (
        <div className="flex flex-col items-start gap-2 border-t border-line-subtle pt-8">
          <p className="font-medium">No products yet</p>
          <p className="text-secondary">
            {canManage
              ? "Add the first product to start this organization's catalog."
              : "An admin of this organization adds products here."}
          </p>
          {canManage && (
            <Link
              href={`/organizations/${id}/products/new`}
              className={buttonClasses("primary")}
            >
              New product
            </Link>
          )}
        </div>
      ) : (
        <section aria-labelledby="products-heading" className="flex flex-col gap-3">
          <h2 id="products-heading" className="sr-only">
            Product list
          </h2>
          <ProductFilterBar
            listPath={listPath}
            filters={filters}
            categories={categories}
          />
          {products.totalElements === 0 ? (
            <div className="flex flex-col items-start gap-2 border-t border-line-subtle pt-8">
              <p className="font-medium">No products match these filters</p>
              <p className="text-secondary">
                Try a different search, category or stock status.
              </p>
              <Link href={listPath} className={buttonClasses("secondary")}>
                Reset filters
              </Link>
            </div>
          ) : (
            <>
              <ProductTable
                orgId={id}
                products={products.content}
                canManage={canManage}
              />
              {range && (
                <p className="text-sm text-secondary tabular-nums">
                  Showing {range.from}–{range.to} of {products.totalElements} products
                </p>
              )}
              <OrgPagination
                label="Products pages"
                page={page}
                pageCount={count}
                previousHref={page > 0 ? pageLink(page - 1) : undefined}
                nextHref={page + 1 < count ? pageLink(page + 1) : undefined}
              />
            </>
          )}
        </section>
      )}
    </>
  );
}
