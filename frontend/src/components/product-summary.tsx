import Link from "next/link";
import type { ReactNode } from "react";
import type { Product } from "@/lib/product-api";
import { formatPrice } from "@/lib/format";
import { LocalTime } from "./local-time";
import { buttonClasses } from "./ui/classes";
import { StockStatusBadge } from "./stock-status-badge";

type Props = {
  orgId: string;
  product: Product;
  /** Only an ADMIN sees the edit link. Everyone reads the facts. */
  canManage: boolean;
};

const DASH = "—";

/** The product's facts as label and value rows, its description, and the edit link. */
export default function ProductSummary({ orgId, product, canManage }: Props) {
  const facts: { label: string; value: ReactNode }[] = [
    {
      label: "Unit price",
      value: <span className="figure">{formatPrice(product.unitPrice)}</span>,
    },
    {
      label: "Stock on hand",
      value: <span className="figure">{product.stockQuantity}</span>,
    },
    {
      label: "Reorder level",
      value: <span className="figure">{product.reorderLevel}</span>,
    },
    {
      label: "Stock status",
      value: <StockStatusBadge status={product.stockStatus} />,
    },
    { label: "Category", value: product.categoryName ?? DASH },
    {
      label: "SKU",
      value: <span className="figure text-caption">{product.sku ?? DASH}</span>,
    },
    {
      label: "Last updated",
      value: (
        <LocalTime
          iso={product.updatedAt}
          kind="date"
          className="figure text-caption text-secondary"
        />
      ),
    },
  ];

  return (
    <section
      aria-labelledby="product-summary-heading"
      className="flex flex-col gap-3"
    >
      <div className="flex min-h-10 items-center justify-between gap-4">
        <h2 id="product-summary-heading" className="text-lg font-medium">
          Details
        </h2>
        {canManage && (
          <Link
            href={`/organizations/${orgId}/products/${product.id}/edit`}
            className={buttonClasses("secondary")}
          >
            Edit product
          </Link>
        )}
      </div>
      <dl className="flex flex-col divide-y divide-line-subtle rounded-control border border-line">
        {facts.map((fact) => (
          <div
            key={fact.label}
            className="flex min-h-11 items-center justify-between gap-4 px-4 py-2"
          >
            <dt className="text-sm text-secondary">{fact.label}</dt>
            <dd className="min-w-0 truncate text-right text-primary">
              {fact.value}
            </dd>
          </div>
        ))}
      </dl>
      {product.description && (
        <p className="whitespace-pre-line text-sm text-primary">
          {product.description}
        </p>
      )}
    </section>
  );
}
