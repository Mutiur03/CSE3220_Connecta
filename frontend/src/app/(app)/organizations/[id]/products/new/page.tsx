import type { Metadata } from "next";
import { redirect } from "next/navigation";
import Link from "next/link";
import ProductForm from "@/components/product-form";
import { createProduct } from "@/lib/product-actions";
import { canManageProducts } from "@/lib/product-permissions";
import { loadOrganization } from "../../data";

type RouteParams = Promise<{ id: string }>;

export const metadata: Metadata = { title: "New product" };

export default async function NewProductPage({ params }: { params: RouteParams }) {
  const { id } = await params;
  const organization = await loadOrganization(id);
  const productsHref = `/organizations/${id}/products`;
  // Only an ADMIN may create products. An AGENT is sent back to the read-only list.
  if (!canManageProducts(organization.callerRole)) redirect(productsHref);

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
        defaultName=""
        defaultDescription=""
        submitLabel="Create product"
        busyLabel="Creating…"
        cancelHref={productsHref}
      />
    </>
  );
}
