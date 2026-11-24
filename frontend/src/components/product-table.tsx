import Image from "next/image";
import Link from "next/link";
import { deleteProduct } from "@/lib/product-actions";
import type { Product } from "@/lib/product-api";
import { formatPrice } from "@/lib/format";
import { buttonClasses } from "./ui/classes";
import OrgActionButton from "./org-action-button";
import { tableClass } from "./org-styles";
import ProductImagePlaceholder from "./product-image-placeholder";
import { StockStatusBadge } from "./stock-status-badge";
import { TableScroll } from "./ui/table-scroll";

type Props = {
  orgId: string;
  products: Product[];
  /** Proxy address of the first image, keyed by product id. A product without one shows a placeholder. */
  thumbnails: Record<string, string>;
  /** Only an ADMIN sees the edit and delete controls. AGENT gets a read-only table. */
  canManage: boolean;
};

const DASH = "—";

/** The catalog as rows. Wide tables scroll inside their named region. */
export default function ProductTable({
  orgId,
  products,
  thumbnails,
  canManage,
}: Props) {
  return (
    <TableScroll label="Products">
      <table className={tableClass}>
        <thead className="border-b border-line text-secondary">
          <tr>
            <th scope="col" className="w-14 px-4 py-2 font-medium">
              <span className="sr-only">Image</span>
            </th>
            <th scope="col" className="px-4 py-2 font-medium">
              Name
            </th>
            <th scope="col" className="px-4 py-2 font-medium">
              SKU
            </th>
            <th scope="col" className="px-4 py-2 font-medium">
              Category
            </th>
            <th scope="col" className="px-4 py-2 text-right font-medium">
              Unit price
            </th>
            <th scope="col" className="px-4 py-2 text-right font-medium">
              Stock
            </th>
            <th scope="col" className="px-4 py-2 font-medium">
              Status
            </th>
            <th scope="col" className="px-4 py-2 text-right font-medium">
              <span className="sr-only">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {products.map((product) => (
            <tr
              key={product.id}
              className="h-14 border-b border-line-subtle transition-colors last:border-b-0 hover:bg-hover/60"
            >
              <td className="px-4 py-2 align-middle">
                {thumbnails[product.id] ? (
                  <span className="relative block size-10 overflow-hidden rounded-control border border-line bg-hover">
                    <Image
                      src={thumbnails[product.id]}
                      alt={product.name}
                      fill
                      unoptimized
                      loading="lazy"
                      sizes="40px"
                      className="object-cover"
                    />
                  </span>
                ) : (
                  <ProductImagePlaceholder />
                )}
              </td>
              <td className="min-w-44 px-4 py-2 align-middle">
                <Link
                  href={`/organizations/${orgId}/products/${product.id}`}
                  className="font-medium underline-offset-4 hover:underline focus-visible:underline"
                >
                  {product.name}
                </Link>
                {product.description && (
                  <p className="max-w-md truncate text-caption text-secondary">
                    {product.description}
                  </p>
                )}
              </td>
              <td className="figure px-4 py-2 align-middle whitespace-nowrap text-caption text-secondary">
                {product.sku ?? DASH}
              </td>
              <td className="px-4 py-2 align-middle text-secondary">
                {product.categoryName ?? DASH}
              </td>
              <td className="figure px-4 py-2 text-right align-middle">
                {formatPrice(product.unitPrice)}
              </td>
              <td className="figure px-4 py-2 text-right align-middle">
                {product.stockQuantity}
              </td>
              <td className="px-4 py-2 align-middle">
                <StockStatusBadge status={product.stockStatus} />
              </td>
              <td className="px-4 py-2 align-middle">
                {canManage && (
                  <div className="flex items-center justify-end gap-2">
                    <Link
                      href={`/organizations/${orgId}/products/${product.id}/edit`}
                      aria-label={`Edit ${product.name}`}
                      className={buttonClasses("secondary", "sm")}
                    >
                      Edit
                    </Link>
                    <OrgActionButton
                      action={deleteProduct.bind(null, orgId, product.id)}
                      label="Delete"
                      pendingLabel="Deleting…"
                      ariaLabel={`Delete ${product.name}`}
                      confirm={`Delete ${product.name}? This cannot be undone.`}
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
