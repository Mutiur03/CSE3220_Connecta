import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import OrgPagination from "@/components/org-pagination";
import ProductImageGallery from "@/components/product-image-gallery";
import ProductStockForm from "@/components/product-stock-form";
import ProductSummary from "@/components/product-summary";
import ProductMovementTable from "@/components/product-movement-table";
import { StockStatusBadge } from "@/components/stock-status-badge";
import { recordStockMovement } from "@/lib/stock-actions";
import { getProduct } from "@/lib/product-api";
import { listProductImages } from "@/lib/product-image-api";
import { listStockMovements } from "@/lib/stock-api";
import { canManageProducts } from "@/lib/product-permissions";
import {
  ORG_PAGE_SIZE,
  pageCount,
  pageHref,
  pageRange,
  parsePageParam,
} from "@/lib/org-pagination";
import { isUuid } from "@/lib/org-validation";
import { loadOrganization, loadSession, notFoundOnMissing } from "../../data";

type RouteParams = Promise<{ id: string; productId: string }>;
type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export const metadata: Metadata = { title: "Product" };

// Two columns from lg up, as a resource details page: the work (stock, history, images) in the
// main column and the product's facts beside it. Below lg the facts come first, then the work.
export default async function ProductDetailPage({
  params,
  searchParams,
}: {
  params: RouteParams;
  searchParams: SearchParams;
}) {
  const { id, productId } = await params;
  if (!isUuid(productId)) notFound();
  const query = await searchParams;
  const page = parsePageParam(query.page);

  const [organization, { accessToken }] = await Promise.all([
    loadOrganization(id),
    loadSession(id),
  ]);
  const [product, movements, images] = await Promise.all([
    notFoundOnMissing(getProduct(accessToken, id, productId)),
    notFoundOnMissing(
      listStockMovements(accessToken, id, productId, page, ORG_PAGE_SIZE),
    ),
    notFoundOnMissing(listProductImages(accessToken, id, productId)),
  ]);

  const canManage = canManageProducts(organization.callerRole);
  const listPath = `/organizations/${id}/products`;
  const detailPath = `${listPath}/${productId}`;
  const range = pageRange(page, movements.size, movements.totalElements);
  const count = pageCount(movements.totalElements, movements.size);

  return (
    <>
      <header className="flex flex-col gap-1">
        <Link
          href={listPath}
          className="self-start text-sm text-secondary underline-offset-4 hover:underline"
        >
          Products
        </Link>
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
          <h1 className="min-w-0 break-words text-display font-medium">
            {product.name}
          </h1>
          <StockStatusBadge status={product.stockStatus} />
        </div>
        {product.categoryName && (
          <p className="text-secondary">{product.categoryName}</p>
        )}
      </header>

      <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_18rem] lg:items-start">
        <div className="lg:col-start-2 lg:row-start-1">
          <ProductSummary orgId={id} product={product} canManage={canManage} />
        </div>

        <div className="flex min-w-0 flex-col gap-8 lg:col-start-1 lg:row-start-1">
          <section
            aria-labelledby="stock-heading"
            className="flex flex-col gap-4"
          >
            <div className="flex flex-col gap-1">
              <h2 id="stock-heading" className="text-lg font-medium">
                Stock
              </h2>
              <p className="text-sm text-secondary">
                Record stock received, stock issued, or a count that corrects
                the level
              </p>
            </div>
            <ProductStockForm
              action={recordStockMovement.bind(null, id, productId)}
            />
          </section>

          <section
            aria-labelledby="history-heading"
            className="flex flex-col gap-3 border-t border-line-subtle pt-8"
          >
            <h2 id="history-heading" className="text-lg font-medium">
              History
            </h2>
            <ProductMovementTable movements={movements.content} />
            <div className="flex flex-wrap items-center justify-between gap-3">
              {range && (
                <p className="figure text-caption text-secondary">
                  {range.from}–{range.to} of {movements.totalElements} movements
                </p>
              )}
              <OrgPagination
                label="Stock movement pages"
                page={page}
                pageCount={count}
                previousHref={
                  page > 0
                    ? pageHref(detailPath, {}, "page", page - 1)
                    : undefined
                }
                nextHref={
                  page + 1 < count
                    ? pageHref(detailPath, {}, "page", page + 1)
                    : undefined
                }
              />
            </div>
          </section>

          <section
            aria-labelledby="images-heading"
            className="flex flex-col gap-4 border-t border-line-subtle pt-8"
          >
            <h2 id="images-heading" className="text-lg font-medium">
              Images
            </h2>
            <ProductImageGallery
              orgId={id}
              productId={productId}
              productName={product.name}
              images={images}
              canManage={canManage}
            />
          </section>
        </div>
      </div>
    </>
  );
}
