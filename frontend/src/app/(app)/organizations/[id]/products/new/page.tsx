import type { Metadata } from "next";
import { redirect } from "next/navigation";
import Link from "next/link";
import ProductForm from "@/components/product-form";
import { createProduct } from "@/lib/product-actions";
import { listCategoryOptions } from "@/lib/product-api";
import { canManageProducts } from "@/lib/product-permissions";
import { loadOrganization, loadSession, notFoundOnMissing } from "../../data";

type RouteParams = Promise<{ id: string }>;

export const metadata: Metadata = { title: "New product" };

const EMPTY_FORM = {
  name: "",
  description: "",
  sku: "",
  unitPrice: "0.00",
  reorderLevel: "0",
  categoryId: "",
};

export default async function NewProductPage({ params }: { params: RouteParams }) {
  const { id } = await params;
  const organization = await loadOrganization(id);
  const productsHref = `/organizations/${id}/products`;
  // Only an ADMIN may create products. An AGENT is sent back to the read-only list.
  if (!canManageProducts(organization.callerRole)) redirect(productsHref);

  const { accessToken } = await loadSession(id);
  const categories = await notFoundOnMissing(listCategoryOptions(accessToken, id));

  return (
    <>
      <header className="flex flex-col gap-2">
        <Link href={productsHref} className="text-sm text-secondary underline-offset-4 hover:underline">
          Products
        </Link>
        <h1 className="text-display font-medium">New product</h1>
      </header>
      <ProductForm
        action={createProduct.bind(null, id)}
        defaults={EMPTY_FORM}
        categories={categories}
        submitLabel="Create product"
        busyLabel="Creating…"
        cancelHref={productsHref}
      />
    </>
  );
}
