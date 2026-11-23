import type { Metadata } from "next";
import Link from "next/link";
import { PackageIcon } from "@/components/icons";
import { InventorySummaryStats } from "@/components/inventory-summary-stats";
import { StockAlertList } from "@/components/stock-alert-list";
import { StockMovementTable } from "@/components/stock-movement-table";
import { Alert } from "@/components/ui/alert";
import { buttonClasses } from "@/components/ui/classes";
import { getInventorySummary, listStockMovements } from "@/lib/inventory-api";
import {
  ALERT_LIST_SIZE,
  RECENT_MOVEMENTS_SIZE,
  inventoryLinks,
  section,
  type Section,
} from "@/lib/inventory-view";
import { listProducts, type Product } from "@/lib/product-api";
import type { Page } from "@/lib/org-api";
import { canManageProducts } from "@/lib/product-permissions";
import { loadOrganization, loadSession } from "../data";

type RouteParams = Promise<{ id: string }>;

export const metadata: Metadata = { title: "Inventory" };

// Every section loads in parallel with the organization, and a failed section shows its own alert.
export default async function InventoryPage({
  params,
}: {
  params: RouteParams;
}) {
  const { id } = await params;
  const { accessToken } = await loadSession(id);
  const [organization, summary, lowStock, outOfStock, movements] =
    await Promise.all([
      loadOrganization(id),
      section(getInventorySummary(accessToken, id)),
      section(
        listProducts(
          accessToken,
          id,
          { stockStatus: "LOW_STOCK" },
          0,
          ALERT_LIST_SIZE,
        ),
      ),
      section(
        listProducts(
          accessToken,
          id,
          { stockStatus: "OUT_OF_STOCK" },
          0,
          ALERT_LIST_SIZE,
        ),
      ),
      section(listStockMovements(accessToken, id, 0, RECENT_MOVEMENTS_SIZE)),
    ]);

  const links = inventoryLinks(id);
  const canManage = canManageProducts(organization.callerRole);
  const hasProducts = summary.ok && summary.value.productCount > 0;

  return (
    <>
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex flex-col gap-1">
          <Link
            href={`/organizations/${id}`}
            className="self-start text-sm text-secondary underline-offset-4 hover:underline"
          >
            {organization.name}
          </Link>
          <h1 className="text-display font-medium">Inventory</h1>
          <p className="text-secondary">
            Stock across your products, what needs restocking, and the latest
            changes
          </p>
        </div>
        <Link href={links.products} className={buttonClasses("secondary")}>
          All products
        </Link>
      </header>

      {summary.ok ? (
        <InventorySummaryStats summary={summary.value} links={links} />
      ) : (
        <Alert tone="danger">
          Inventory totals could not be loaded. Refresh the page to try again.
        </Alert>
      )}

      {summary.ok && !hasProducts ? (
        <div className="flex flex-wrap items-start gap-x-4 gap-y-3 rounded-control border border-line p-4">
          <span className="grid size-10 shrink-0 place-items-center rounded-control border border-line bg-app text-primary">
            <PackageIcon />
          </span>
          <div className="flex min-w-0 flex-1 basis-56 flex-col gap-1">
            <p className="font-medium">No products yet</p>
            <p className="text-sm text-secondary">
              {canManage
                ? "Add a product with its stock level and reorder level to start tracking inventory."
                : "An admin of this organization adds products, and their stock shows here."}
            </p>
          </div>
          {canManage && (
            <Link href={links.newProduct} className={buttonClasses("primary")}>
              New product
            </Link>
          )}
        </div>
      ) : (
        <>
          <div className="grid gap-8 md:grid-cols-2">
            <StockAlertList
              headingId="low-stock-heading"
              title="Low stock"
              {...alertProps(
                lowStock,
                links.lowStock,
                links.product,
                "Every product is above its reorder level.",
              )}
            />
            <StockAlertList
              headingId="out-of-stock-heading"
              title="Out of stock"
              {...alertProps(
                outOfStock,
                links.outOfStock,
                links.product,
                "Every product has stock on hand.",
              )}
            />
          </div>

          <section
            aria-labelledby="movements-heading"
            className="flex flex-col gap-3"
          >
            <h2 id="movements-heading" className="text-lg font-medium">
              Recent movements
            </h2>
            {movements.ok ? (
              <StockMovementTable
                movements={movements.value.content}
                productHref={links.product}
                productsHref={links.products}
              />
            ) : (
              <Alert tone="danger">
                Recent movements could not be loaded. Refresh the page to try
                again.
              </Alert>
            )}
          </section>
        </>
      )}
    </>
  );
}

// The alert list props for one section. A failed request shows an alert instead of an empty list.
function alertProps(
  result: Section<Page<Product>>,
  viewAllHref: string,
  productHref: (productId: string) => string,
  emptyText: string,
) {
  return {
    products: result.ok ? result.value.content : [],
    total: result.ok ? result.value.totalElements : 0,
    failed: !result.ok,
    emptyText,
    viewAllHref,
    productHref,
  };
}
