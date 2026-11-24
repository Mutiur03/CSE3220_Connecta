import type { Metadata } from "next";
import { redirect } from "next/navigation";
import Link from "next/link";
import CategoryForm from "@/components/category-form";
import { createCategory } from "@/lib/category-actions";
import { canManageCategories } from "@/lib/category-permissions";
import { loadOrganization } from "../../data";

type RouteParams = Promise<{ id: string }>;

export const metadata: Metadata = { title: "New category" };

export default async function NewCategoryPage({
  params,
}: {
  params: RouteParams;
}) {
  const { id } = await params;
  const organization = await loadOrganization(id);
  const categoriesHref = `/organizations/${id}/categories`;
  // Only an ADMIN may create categories. An AGENT is sent back to the read-only list.
  if (!canManageCategories(organization.callerRole)) redirect(categoriesHref);

  return (
    <>
      <header className="flex flex-col gap-1">
        <Link
          href={categoriesHref}
          className="self-start text-sm text-secondary underline-offset-4 hover:underline"
        >
          Categories
        </Link>
        <h1 className="text-display font-medium">New category</h1>
        <p className="text-secondary">
          Once it exists, pick it on a product to file the product under it.
        </p>
      </header>
      <CategoryForm
        action={createCategory.bind(null, id)}
        defaultName=""
        defaultDescription=""
        submitLabel="Create category"
        busyLabel="Creating…"
        cancelHref={categoriesHref}
      />
    </>
  );
}
