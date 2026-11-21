import Link from "next/link";
import type { Product } from "@/lib/product-api";
import { formatPrice } from "@/lib/format";
import { buttonClasses } from "./ui/classes";
import { StockStatusBadge } from "./stock-status-badge";

type Props = {
  orgId: string;
  product: Product;
  /** Only an ADMIN sees the edit link. Everyone reads the facts. */
  canManage: boolean;
};

const DASH = "—";

/** The product's facts as a label and value grid, with its description and the edit link. */
export default function ProductSummary({ orgId, product, canManage }: Props) {
  const facts: { label: string; value: React.ReactNode }[] = [
    { label: "SKU", value: product.sku ?? DASH },
    { label: "Category", value: product.categoryName ?? DASH },
    { label: "Unit price", value: formatPrice(product.unitPrice) },
    { label: "Stock on hand", value: <span className="tabular-nums">{product.stockQuantity}</span> },
    { label: "Reorder level", value: <span className="tabular-nums">{product.reorderLevel}</span> },
    { label: "Stock status", value: <StockStatusBadge status={product.stockStatus} /> },
  ];

  return (
    <section aria-labelledby="product-summary-heading" className="flex flex-col gap-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <h2 id="product-summary-heading" className="sr-only">
          Product details
        </h2>
        {canManage && (
          <Link
            href={`/organizations/${orgId}/products/${product.id}/edit`}
            aria-label={`Edit ${product.name}`}
            className={buttonClasses("secondary")}
          >
            Edit product
          </Link>
        )}
      </div>
      <dl className="grid grid-cols-2 gap-x-8 gap-y-4 sm:grid-cols-3">
        {facts.map((fact) => (
          <div key={fact.label} className="flex flex-col gap-1">
            <dt className="text-sm text-secondary">{fact.label}</dt>
            <dd className="text-primary">{fact.value}</dd>
          </div>
        ))}
      </dl>
      {product.description && (
        <p className="max-w-2xl whitespace-pre-line text-primary">{product.description}</p>
      )}
    </section>
  );
}
