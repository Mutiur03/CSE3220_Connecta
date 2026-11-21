import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import Link from "next/link";
import ProductForm from "@/components/product-form";
import { updateProduct } from "@/lib/product-actions";
import { getProduct } from "@/lib/product-api";
import { canManageProducts } from "@/lib/product-permissions";
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
  const product = await notFoundOnMissing(getProduct(accessToken, id, productId));

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
        defaultName={product.name}
        defaultDescription={product.description ?? ""}
        submitLabel="Save product"
        busyLabel="Saving…"
        cancelHref={productsHref}
      />
    </>
  );
}
