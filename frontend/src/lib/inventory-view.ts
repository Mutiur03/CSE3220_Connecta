import { formatPrice } from "./format";
import type { StockMovementType } from "./inventory-api";
import { productsHref } from "./product-validation";

/** Rows in each stock alert list and in the recent movements table. */
export const ALERT_LIST_SIZE = 10;
export const RECENT_MOVEMENTS_SIZE = 10;

const MOVEMENT_TYPE_LABELS: Record<StockMovementType, string> = {
  RECEIVE: "Received",
  ISSUE: "Issued",
  ADJUST: "Adjusted",
};

/** The stock value in the organization's money format: two decimals, as the product list shows prices. */
export const formatStockValue = (value: number): string => formatPrice(value);

export const formatCount = (count: number): string =>
  count.toLocaleString("en-US");

export const movementTypeLabel = (type: StockMovementType): string =>
  MOVEMENT_TYPE_LABELS[type];

/** A signed change, such as +5 or -3. A zero change is shown without a sign. */
export function formatQuantityDelta(delta: number): string {
  if (delta > 0) return `+${delta}`;
  if (delta < 0) return `-${Math.abs(delta)}`;
  return "0";
}

/** Stock against the reorder level, as one line: "2 left, reorder at 5", or "None left" when out. */
export function stockLine(quantity: number, reorderLevel: number): string {
  const left = quantity > 0 ? `${formatCount(quantity)} left` : "None left";
  return `${left}, reorder at ${formatCount(reorderLevel)}`;
}

export const formatMovementTime = (iso: string): string =>
  new Date(iso).toLocaleString("en", {
    dateStyle: "medium",
    timeStyle: "short",
  });

/** Where each inventory link goes. The product list is the organization's catalog page. */
export function inventoryLinks(orgId: string) {
  const products = `/organizations/${orgId}/products`;
  return {
    products,
    newProduct: `${products}/new`,
    lowStock: productsHref(products, { stockStatus: "LOW_STOCK" }),
    outOfStock: productsHref(products, { stockStatus: "OUT_OF_STOCK" }),
    product: (productId: string) => `${products}/${productId}`,
  };
}

export type Section<T> = { ok: true; value: T } | { ok: false };

/**
 * Settles one section's request so a failure shows that section's own message while the rest of the
 * page still renders. The failure is shown to the person, not hidden.
 */
export const section = <T>(pending: Promise<T>): Promise<Section<T>> =>
  pending.then(
    (value): Section<T> => ({ ok: true, value }),
    (): Section<T> => ({ ok: false }),
  );
