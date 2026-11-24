import Link from "next/link";
import type { Product } from "@/lib/product-api";
import { formatCount, stockLine } from "@/lib/inventory-view";
import { CircleCheckIcon } from "./icons";
import { Alert } from "./ui/alert";
import { StockStatusBadge } from "./stock-status-badge";

type Props = {
  headingId: string;
  title: string;
  products: Product[];
  /** Every product with this stock status, which can be more than the rows shown. */
  total: number;
  emptyText: string;
  /** The request failed: show an error instead of a list that only looks empty. */
  failed?: boolean;
  viewAllHref: string;
  productHref: (productId: string) => string;
};

const ROW_LIST =
  "flex flex-col divide-y divide-line-subtle rounded-control border border-line";

// The first rows of a stock alert, each with its stock against the reorder level, and a link to the
// full filtered list when there are more than shown.
export function StockAlertList({
  headingId,
  title,
  products,
  total,
  emptyText,
  failed = false,
  viewAllHref,
  productHref,
}: Props) {
  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-3">
      <div className="flex min-h-6 items-baseline justify-between gap-4">
        <h2
          id={headingId}
          className="flex items-baseline gap-2 text-lg font-medium"
        >
          {title}
          {!failed && (
            <span className="figure text-sm text-secondary">
              {formatCount(total)}
            </span>
          )}
        </h2>
        {total > products.length && (
          <Link
            href={viewAllHref}
            className="text-sm text-secondary underline-offset-4 hover:text-primary hover:underline"
          >
            View all {formatCount(total)}
          </Link>
        )}
      </div>
      {failed ? (
        <Alert tone="danger">
          This list could not be loaded. Refresh the page to try again.
        </Alert>
      ) : products.length === 0 ? (
        <p className="flex min-h-14 items-center gap-2.5 rounded-control border border-line px-4 text-sm text-secondary [&_svg]:size-4 [&_svg]:shrink-0 [&_svg]:text-success">
          <CircleCheckIcon />
          {emptyText}
        </p>
      ) : (
        <ul className={ROW_LIST}>
          {products.map((product) => (
            <li
              key={product.id}
              className="flex min-h-14 items-center justify-between gap-4 px-4 py-2"
            >
              <span className="flex min-w-0 flex-col">
                <Link
                  href={productHref(product.id)}
                  className="truncate text-primary underline-offset-4 hover:underline"
                >
                  {product.name}
                </Link>
                <span className="figure truncate text-caption text-secondary">
                  {stockLine(product.stockQuantity, product.reorderLevel)}
                </span>
              </span>
              <StockStatusBadge status={product.stockStatus} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
