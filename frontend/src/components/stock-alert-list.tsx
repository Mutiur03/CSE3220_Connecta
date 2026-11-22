import Link from "next/link";
import type { Product } from "@/lib/product-api";
import { formatCount } from "@/lib/inventory-view";
import { StockStatusBadge } from "./stock-status-badge";

type Props = {
  headingId: string;
  title: string;
  products: Product[];
  /** Every product with this stock status, which can be more than the rows shown. */
  total: number;
  emptyText: string;
  viewAllHref: string;
  productHref: (productId: string) => string;
};

// The first rows of a stock alert, each linking to the product, with the full filtered list below.
export function StockAlertList({
  headingId,
  title,
  products,
  total,
  emptyText,
  viewAllHref,
  productHref,
}: Props) {
  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-3">
      <div className="flex items-baseline justify-between gap-4">
        <h2 id={headingId} className="text-lg font-medium">
          {title}
        </h2>
        {total > 0 && (
          <Link
            href={viewAllHref}
            className="text-sm text-secondary underline-offset-4 hover:underline"
          >
            View all {formatCount(total)}
          </Link>
        )}
      </div>
      {products.length === 0 ? (
        <p className="border-t border-line-subtle pt-3 text-secondary">{emptyText}</p>
      ) : (
        <ul className="divide-y divide-line-subtle border-t border-line-subtle">
          {products.map((product) => (
            <li key={product.id} className="flex h-12 items-center justify-between gap-4">
              <Link
                href={productHref(product.id)}
                className="min-w-0 truncate font-medium underline-offset-4 hover:underline"
              >
                {product.name}
              </Link>
              <span className="flex shrink-0 items-center gap-3 tabular-nums">
                <span className="text-secondary">{product.stockQuantity} in stock</span>
                <StockStatusBadge status={product.stockStatus} />
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
