// Client-side checks that mirror the Product rules on the backend. The backend validates again.
export const PRODUCT_NAME_MAX_LENGTH = 150;
export const PRODUCT_DESCRIPTION_MAX_LENGTH = 1000;
export const PRODUCT_SKU_MAX_LENGTH = 64;
export const PRODUCT_QUERY_MAX_LENGTH = 100;
const PRICE_INTEGER_DIGITS = 10;
const PRICE_FRACTION_DIGITS = 2;
const REORDER_LEVEL_MAX = 2_147_483_647;
const PRICE_MESSAGE = "Enter a price of 0 or more, such as 12.50.";

export const STOCK_STATUSES = ["IN_STOCK", "LOW_STOCK", "OUT_OF_STOCK"] as const;
export type StockStatus = (typeof STOCK_STATUSES)[number];

export const STOCK_STATUS_LABELS: Record<StockStatus, string> = {
  IN_STOCK: "In stock",
  LOW_STOCK: "Low stock",
  OUT_OF_STOCK: "Out of stock",
};

export function validateProductName(name: string): string | undefined {
  if (!name) return "Enter a name for the product.";
  if (name.length > PRODUCT_NAME_MAX_LENGTH)
    return `Use at most ${PRODUCT_NAME_MAX_LENGTH} characters.`;
  return undefined;
}

export function validateProductDescription(
  description: string,
): string | undefined {
  if (description.length > PRODUCT_DESCRIPTION_MAX_LENGTH)
    return `Use at most ${PRODUCT_DESCRIPTION_MAX_LENGTH} characters.`;
  return undefined;
}

/** A blank SKU is allowed: it means the product has none. */
export function validateProductSku(sku: string): string | undefined {
  if (sku.length > PRODUCT_SKU_MAX_LENGTH)
    return `Use at most ${PRODUCT_SKU_MAX_LENGTH} characters.`;
  return undefined;
}

/** A blank price means 0.00, as the backend reads a missing unitPrice. */
export function validateUnitPrice(price: string): string | undefined {
  if (price === "") return undefined;
  const match = /^(\d+)(?:\.(\d+))?$/.exec(price);
  if (!match) return PRICE_MESSAGE;
  const [, whole, fraction = ""] = match;
  if (fraction.length > PRICE_FRACTION_DIGITS)
    return `Use at most ${PRICE_FRACTION_DIGITS} decimal places.`;
  if (whole.replace(/^0+/, "").length > PRICE_INTEGER_DIGITS)
    return `Use at most ${PRICE_INTEGER_DIGITS} digits before the decimal point.`;
  return undefined;
}

export function parseUnitPrice(price: string): number {
  return price === "" ? 0 : Number(price);
}

/** A blank reorder level means 0, which turns the low-stock alert off. */
export function validateReorderLevel(level: string): string | undefined {
  if (level === "") return undefined;
  if (!/^\d+$/.test(level)) return "Enter a whole number, 0 or more.";
  if (Number(level) > REORDER_LEVEL_MAX)
    return `Use a number up to ${REORDER_LEVEL_MAX}.`;
  return undefined;
}

export function parseReorderLevel(level: string): number {
  return level === "" ? 0 : Number(level);
}

export type ProductFilters = {
  q?: string;
  categoryId?: string;
  stockStatus?: StockStatus;
};

type SearchParams = Record<string, string | string[] | undefined>;

const firstValue = (raw: string | string[] | undefined) =>
  (Array.isArray(raw) ? raw[0] : raw) ?? "";

/**
 * The filters the URL asks for, limited to what the list can use. An unknown stock status is
 * dropped, and so is a category that is not in the organization's list (the backend would 404).
 */
export function parseProductFilters(
  params: SearchParams,
  categoryIds: readonly string[],
): ProductFilters {
  const q = firstValue(params.q).trim().slice(0, PRODUCT_QUERY_MAX_LENGTH);
  const categoryId = firstValue(params.categoryId);
  const stockStatus = firstValue(params.stockStatus);
  return {
    q: q || undefined,
    categoryId: categoryIds.includes(categoryId) ? categoryId : undefined,
    stockStatus: STOCK_STATUSES.find((status) => status === stockStatus),
  };
}

export function hasProductFilters(filters: ProductFilters): boolean {
  return Boolean(filters.q || filters.categoryId || filters.stockStatus);
}

/**
 * The query string for the list: its filters and page, as the URL and the API both read them. Unset
 * filters and page 0 are left out, so the first unfiltered page has a clean address.
 */
export function productListQuery(
  filters: ProductFilters,
  page = 0,
  size?: number,
): string {
  const query = new URLSearchParams();
  if (filters.q) query.set("q", filters.q);
  if (filters.categoryId) query.set("categoryId", filters.categoryId);
  if (filters.stockStatus) query.set("stockStatus", filters.stockStatus);
  if (page > 0) query.set("page", String(page));
  if (size !== undefined) query.set("size", String(size));
  return query.toString();
}

/** The list page's address for these filters and page. */
export function productsHref(
  listPath: string,
  filters: ProductFilters,
  page = 0,
): string {
  const search = productListQuery(filters, page);
  return search ? `${listPath}?${search}` : listPath;
}
