import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import OrgPagination from "@/components/org-pagination";
import ProductImageGallery from "@/components/product-image-gallery";
import ProductStockForm from "@/components/product-stock-form";
import ProductSummary from "@/components/product-summary";
import ProductMovementTable from "@/components/product-movement-table";
import { recordStockMovement } from "@/lib/stock-actions";
import { getProduct } from "@/lib/product-api";
import { listProductImages } from "@/lib/product-image-api";
import { PRODUCT_IMAGE_LIMIT } from "@/lib/product-image-validation";
import { listStockMovements } from "@/lib/stock-api";
import { canManageProducts } from "@/lib/product-permissions";
import { ORG_PAGE_SIZE, pageCount, pageHref, pageRange, parsePageParam } from "@/lib/org-pagination";
import { isUuid } from "@/lib/org-validation";
import { loadOrganization, loadSession, notFoundOnMissing } from "../../data";

type RouteParams = Promise<{ id: string; productId: string }>;
type SearchParams = Promise<Record<string, string | string[] | undefined>>;

export const metadata: Metadata = { title: "Product" };

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
    notFoundOnMissing(listStockMovements(accessToken, id, productId, page, ORG_PAGE_SIZE)),
    notFoundOnMissing(listProductImages(accessToken, id, productId)),
  ]);

  const canManage = canManageProducts(organization.callerRole);
  const listPath = `/organizations/${id}/products`;
  const detailPath = `${listPath}/${productId}`;
  const range = pageRange(page, movements.size, movements.totalElements);
  const count = pageCount(movements.totalElements, movements.size);

  return (
    <>
      <header className="flex flex-col gap-2">
        <Link href={listPath} className="text-sm text-secondary underline-offset-4 hover:underline">
          Products
        </Link>
        <h1 className="text-display font-medium">{product.name}</h1>
      </header>

      <ProductSummary orgId={id} product={product} canManage={canManage} />

      <section aria-labelledby="stock-heading" className="flex flex-col gap-6 border-t border-line-subtle pt-8">
        <h2 id="stock-heading" className="text-lg font-medium">
          Stock
        </h2>
        <ProductStockForm action={recordStockMovement.bind(null, id, productId)} />
        <div className="flex flex-col gap-3">
          <h3 className="font-medium">History</h3>
          <ProductMovementTable movements={movements.content} />
          {range && (
            <p className="text-sm text-secondary tabular-nums">
              Showing {range.from}–{range.to} of {movements.totalElements} movements
            </p>
          )}
          <OrgPagination
            label="Stock movement pages"
            page={page}
            pageCount={count}
            previousHref={page > 0 ? pageHref(detailPath, {}, "page", page - 1) : undefined}
            nextHref={page + 1 < count ? pageHref(detailPath, {}, "page", page + 1) : undefined}
          />
        </div>
      </section>

      <section aria-labelledby="images-heading" className="flex flex-col gap-6 border-t border-line-subtle pt-8">
        <div className="flex flex-col gap-1">
          <h2 id="images-heading" className="text-lg font-medium">
            Images
          </h2>
          <p className="text-secondary">
            {images.length === 0 ? "No images added." : `${images.length} of ${PRODUCT_IMAGE_LIMIT} images.`}
          </p>
        </div>
        <ProductImageGallery
          orgId={id}
          productId={productId}
          productName={product.name}
          images={images}
          canManage={canManage}
        />
      </section>
    </>
  );
}
