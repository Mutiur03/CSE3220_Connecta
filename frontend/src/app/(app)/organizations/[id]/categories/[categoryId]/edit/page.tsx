import type { Metadata } from "next";
import { notFound, redirect } from "next/navigation";
import Link from "next/link";
import CategoryForm from "@/components/category-form";
import { updateCategory } from "@/lib/category-actions";
import { getCategory } from "@/lib/category-api";
import { canManageCategories } from "@/lib/category-permissions";
import { isUuid } from "@/lib/org-validation";
import { loadOrganization, loadSession, notFoundOnMissing } from "../../../data";

type RouteParams = Promise<{ id: string; categoryId: string }>;

export const metadata: Metadata = { title: "Edit category" };

export default async function EditCategoryPage({ params }: { params: RouteParams }) {
  const { id, categoryId } = await params;
  if (!isUuid(categoryId)) notFound();
  const organization = await loadOrganization(id);
  const categoriesHref = `/organizations/${id}/categories`;
  if (!canManageCategories(organization.callerRole)) redirect(categoriesHref);

  const { accessToken } = await loadSession(id);
  const category = await notFoundOnMissing(getCategory(accessToken, id, categoryId));

  return (
    <>
      <header className="flex flex-col gap-2">
        <Link href={categoriesHref} className="text-sm text-secondary underline-offset-4 hover:underline">
          Categories
        </Link>
        <h1 className="text-display font-medium">Edit {category.name}</h1>
      </header>
      <CategoryForm
        action={updateCategory.bind(null, id, categoryId)}
        defaultName={category.name}
        defaultDescription={category.description ?? ""}
        submitLabel="Save category"
        busyLabel="Saving…"
        cancelHref={categoriesHref}
      />
    </>
  );
}
