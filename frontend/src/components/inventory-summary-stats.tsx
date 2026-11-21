import Link from "next/link";
import type { InventorySummary } from "@/lib/inventory-api";
import { formatCount, formatStockValue } from "@/lib/inventory-view";

type Props = {
  summary: InventorySummary;
  links: { products: string; lowStock: string; outOfStock: string };
};

type Stat = { label: string; value: string; linkLabel?: string; href?: string };

// Figures sit in rows and dividers, not in boxes. Each linked figure opens the list it counts.
export function InventorySummaryStats({ summary, links }: Props) {
  const stats: Stat[] = [
    {
      label: "Products",
      value: formatCount(summary.productCount),
      linkLabel: "View products",
      href: links.products,
    },
    { label: "Total units", value: formatCount(summary.totalUnits) },
    { label: "Stock value", value: formatStockValue(summary.stockValue) },
    {
      label: "Low stock",
      value: formatCount(summary.lowStockCount),
      linkLabel: "View low stock",
      href: links.lowStock,
    },
    {
      label: "Out of stock",
      value: formatCount(summary.outOfStockCount),
      linkLabel: "View out of stock",
      href: links.outOfStock,
    },
  ];

  return (
    <dl className="grid grid-cols-2 gap-x-8 gap-y-6 border-t border-line-subtle pt-6 lg:grid-cols-5">
      {stats.map((stat) => (
        <div key={stat.label} className="flex flex-col gap-1">
          <dt className="text-sm text-secondary">{stat.label}</dt>
          <dd className="text-display font-medium tabular-nums">{stat.value}</dd>
          {stat.href && stat.linkLabel && (
            <Link
              href={stat.href}
              className="mt-1 w-fit text-sm text-secondary underline-offset-4 hover:underline"
            >
              {stat.linkLabel}
            </Link>
          )}
        </div>
      ))}
    </dl>
  );
}
