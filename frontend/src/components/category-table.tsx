import Link from "next/link";
import { deleteCategory } from "@/lib/category-actions";
import type { Category } from "@/lib/category-api";
import { formatDate } from "@/lib/format";
import { buttonClasses } from "./ui/classes";
import OrgActionButton from "./org-action-button";
import { tableClass } from "./org-styles";
import { TableScroll } from "./ui/table-scroll";

type Props = {
  orgId: string;
  categories: Category[];
  /** Only an ADMIN sees the edit and delete controls. AGENT gets a read-only table. */
  canManage: boolean;
};

const DASH = "—";

/** The categories as rows. Wide tables scroll inside their named region. */
export default function CategoryTable({ orgId, categories, canManage }: Props) {
  return (
    <TableScroll label="Categories">
      <table className={tableClass}>
        <thead className="border-b border-line text-secondary">
          <tr>
            <th scope="col" className="px-4 py-2 font-medium">
              Name
            </th>
            <th scope="col" className="px-4 py-2 font-medium">
              Description
            </th>
            <th scope="col" className="px-4 py-2 font-medium">
              Updated
            </th>
            <th scope="col" className="px-4 py-2 text-right font-medium">
              <span className="sr-only">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {categories.map((category) => (
            <tr
              key={category.id}
              className="h-14 border-b border-line-subtle transition-colors last:border-b-0 hover:bg-hover/60"
            >
              <td className="px-4 py-2 align-middle">
                <p className="font-medium">{category.name}</p>
              </td>
              <td className="max-w-md truncate px-4 py-2 align-middle text-secondary">
                {category.description ?? DASH}
              </td>
              <td className="px-4 py-2 align-middle text-secondary">
                {formatDate(category.updatedAt)}
              </td>
              <td className="px-4 py-2 align-middle">
                {canManage && (
                  <div className="flex items-center justify-end gap-2">
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
                      confirm={`Delete ${category.name}? This cannot be undone.`}
                      tone="danger"
                      size="sm"
                    />
                  </div>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </TableScroll>
  );
}
