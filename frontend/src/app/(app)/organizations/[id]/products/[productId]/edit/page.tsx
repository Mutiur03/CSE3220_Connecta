import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import Link from "next/link";
import ProductForm from "@/components/product-form";
import ProductImageGallery from "@/components/product-image-gallery";
import { updateProduct } from "@/lib/product-actions";
import { getProduct, listCategoryOptions } from "@/lib/product-api";
import { listProductImages } from "@/lib/product-image-api";
import { canManageProducts } from "@/lib/product-permissions";
import { formatPrice } from "@/lib/format";
import { isUuid } from "@/lib/org-validation";
import { loadOrganization, loadSession, notFoundOnMissing } from "../../../data";

type RouteParams = Promise<{ id: string; productId: string }>;

export const metadata: Metadata = { title: "Edit product" };

export default async function EditProductPage({ params }: { params: RouteParams }) {
  const { id, productId } = await params;
  if (!isUuid(productId)) notFound();
  const organization = await loadOrganization(id);
  const productsHref = `/organizations/${id}/products`;
  if (!canManageProducts(organization.callerRole)) redirect(productsHref);

  const { accessToken } = await loadSession(id);
  const [product, categories, images] = await Promise.all([
    notFoundOnMissing(getProduct(accessToken, id, productId)),
    notFoundOnMissing(listCategoryOptions(accessToken, id)),
    notFoundOnMissing(listProductImages(accessToken, id, productId)),
  ]);

  return (
    <>
      <header className="flex flex-col gap-2">
        <Link href={productsHref} className="text-sm text-secondary underline-offset-4 hover:underline">
          Products
        </Link>
        <h1 className="text-display font-medium">Edit {product.name}</h1>
      </header>
      <ProductForm
        action={updateProduct.bind(null, id, productId)}
        defaults={{
          name: product.name,
          description: product.description ?? "",
          sku: product.sku ?? "",
          unitPrice: formatPrice(product.unitPrice),
          reorderLevel: String(product.reorderLevel),
          categoryId: product.categoryId ?? "",
        }}
        categories={categories}
        stockQuantity={product.stockQuantity}
        submitLabel="Save product"
        busyLabel="Saving…"
        cancelHref={productsHref}
      />

      <section aria-labelledby="images-heading" className="flex flex-col gap-6 border-t border-line-subtle pt-8">
        <div className="flex flex-col gap-1">
          <h2 id="images-heading" className="text-lg font-medium">
            Images
          </h2>
        </div>
        <ProductImageGallery
          orgId={id}
          productId={productId}
          productName={product.name}
          images={images}
          canManage={canManageProducts(organization.callerRole)}
        />
      </section>
    </>
  );
}
