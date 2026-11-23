import Link from "next/link";
import type { InventorySummary } from "@/lib/inventory-api";
import { formatCount, formatStockValue } from "@/lib/inventory-view";

type Props = {
  summary: InventorySummary;
  links: { products: string; lowStock: string; outOfStock: string };
};

type Stat = {
  label: string;
  value: string;
  link?: { label: string; href: string };
  /** A count that needs a look: low stock gets the brass status dot, out of stock the danger colour. */
  attention?: "dot" | "danger";
  className?: string;
};

// One strip of hairline-divided cells, not a row of big numbers. On a phone it is two columns, with
// stock value across the full width so the five cells fill the grid.
export function InventorySummaryStats({ summary, links }: Props) {
  const stats: Stat[] = [
    {
      label: "Products",
      value: formatCount(summary.productCount),
      link: { label: "View products", href: links.products },
    },
    { label: "Units in stock", value: formatCount(summary.totalUnits) },
    {
      label: "Stock value",
      value: formatStockValue(summary.stockValue),
      className: "col-span-2 lg:col-span-1",
    },
    {
      label: "Low stock",
      value: formatCount(summary.lowStockCount),
      link: { label: "View low stock", href: links.lowStock },
      attention: summary.lowStockCount > 0 ? "dot" : undefined,
    },
    {
      label: "Out of stock",
      value: formatCount(summary.outOfStockCount),
      link: { label: "View out of stock", href: links.outOfStock },
      attention: summary.outOfStockCount > 0 ? "danger" : undefined,
    },
  ];

  return (
    <dl className="grid grid-cols-2 gap-px overflow-hidden rounded-control border border-line bg-line lg:grid-cols-5">
      {stats.map((stat) => (
        <div
          key={stat.label}
          className={[
            "flex flex-col gap-1 bg-surface px-4 py-3",
            stat.className,
          ]
            .filter(Boolean)
            .join(" ")}
        >
          <dt className="flex items-center gap-1.5 text-sm text-secondary">
            {stat.attention === "dot" && (
              <span
                aria-hidden="true"
                className="size-1.5 rounded-full bg-highlight"
              />
            )}
            {stat.label}
          </dt>
          <dd
            className={`figure text-lg font-medium ${stat.attention === "danger" ? "text-danger" : "text-primary"}`}
          >
            {stat.value}
          </dd>
          {stat.link && (
            <dd>
              <Link
                href={stat.link.href}
                className="text-caption text-secondary underline-offset-4 hover:text-primary hover:underline"
              >
                {stat.link.label}
              </Link>
            </dd>
          )}
        </div>
      ))}
    </dl>
  );
}
