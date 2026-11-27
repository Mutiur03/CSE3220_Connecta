import Link from "next/link";
import { deleteCategory } from "@/lib/category-actions";
import type { Category } from "@/lib/category-api";
import { LocalTime } from "./local-time";
import { buttonClasses } from "./ui/classes";
import OrgActionButton from "./org-action-button";

type Props = {
  orgId: string;
  categories: Category[];
  /** Only an ADMIN sees the edit and delete controls. AGENT gets a read-only list. */
  canManage: boolean;
};

/**
 * The categories as a bordered list of rows: name and description on the left, the last update and
 * the row actions on the right. On a narrow screen the right side wraps under the name.
 */
export default function CategoryTable({ orgId, categories, canManage }: Props) {
  return (
    <ul
      aria-label="Categories"
      className="flex flex-col divide-y divide-line-subtle rounded-control border border-line bg-surface"
    >
      {categories.map((category) => (
        <li
          key={category.id}
          className="flex min-h-14 flex-wrap items-center gap-x-4 gap-y-2 px-4 py-2"
        >
          <div className="flex min-w-0 flex-1 basis-48 flex-col">
            <p className="truncate font-medium text-primary">{category.name}</p>
            {category.description && (
              <p className="truncate text-secondary">{category.description}</p>
            )}
          </div>
          <div className="ml-auto flex shrink-0 items-center gap-3">
            <span className="figure text-caption text-secondary">
              Updated <LocalTime iso={category.updatedAt} kind="date" />
            </span>
            {canManage && (
              <div className="flex items-center gap-2">
                <Link
                  href={`/organizations/${orgId}/categories/${category.id}/edit`}
                  aria-label={`Edit ${category.name}`}
                  className={buttonClasses("secondary", "sm")}
                >
                  Edit
                </Link>
                <OrgActionButton
                  action={deleteCategory.bind(null, orgId, category.id)}
                  label="Delete"
                  pendingLabel="Deleting…"
                  ariaLabel={`Delete ${category.name}`}
                  confirm={`Delete ${category.name}? Its products stay in the catalog without a category. This cannot be undone.`}
                  tone="danger"
                  size="sm"
                />
              </div>
            )}
          </div>
        </li>
      ))}
    </ul>
  );
}
