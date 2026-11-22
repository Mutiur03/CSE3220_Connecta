import Link from "next/link";
import type { CategoryOption } from "@/lib/product-api";
import {
  PRODUCT_QUERY_MAX_LENGTH,
  STOCK_STATUSES,
  STOCK_STATUS_LABELS,
  hasProductFilters,
  productsHref,
  type ProductFilters,
} from "@/lib/product-validation";
import { buttonClasses, fieldClasses } from "./ui/classes";
import { Select } from "./ui/select";

type Props = {
  listPath: string;
  filters: ProductFilters;
  categories: CategoryOption[];
};

// A plain GET form: the filters live in the URL, so a filtered list can be shared, reloaded and
// paged without JavaScript. Submitting drops the page number, so a new search starts on page 1.
export default function ProductFilterBar({ listPath, filters, categories }: Props) {
  const clearSearchHref = productsHref(listPath, { ...filters, q: undefined });

  return (
    <form
      role="search"
      aria-label="Filter products"
      method="get"
      action={listPath}
      className="flex flex-wrap items-end gap-3"
    >
      <div className="flex min-w-56 flex-1 flex-col gap-1">
        <label htmlFor="product-search" className="text-sm font-medium text-primary">
          Search
        </label>
        <div className="flex items-center gap-2">
          <input
            id="product-search"
            name="q"
            type="search"
            defaultValue={filters.q ?? ""}
            placeholder="Name or SKU"
            maxLength={PRODUCT_QUERY_MAX_LENGTH}
            autoComplete="off"
            className={`${fieldClasses} [&::-webkit-search-cancel-button]:appearance-none`}
          />
          {filters.q && (
            <Link
              href={clearSearchHref}
              aria-label="Clear search"
              className={buttonClasses("secondary")}
            >
              Clear
            </Link>
          )}
        </div>
      </div>
      <div className="flex min-w-44 flex-col gap-1">
        <label htmlFor="product-category" className="text-sm font-medium text-primary">
          Category
        </label>
        <Select
          id="product-category"
          name="categoryId"
          autoComplete="off"
          defaultValue={filters.categoryId ?? ""}
        >
          <option value="">All categories</option>
          {categories.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </Select>
      </div>
      <div className="flex min-w-44 flex-col gap-1">
        <label htmlFor="product-stock" className="text-sm font-medium text-primary">
          Stock status
        </label>
        <Select
          id="product-stock"
          name="stockStatus"
          autoComplete="off"
          defaultValue={filters.stockStatus ?? ""}
        >
          <option value="">All stock levels</option>
          {STOCK_STATUSES.map((status) => (
            <option key={status} value={status}>
              {STOCK_STATUS_LABELS[status]}
            </option>
          ))}
        </Select>
      </div>
      <div className="flex items-center gap-3">
        <button type="submit" className={buttonClasses("secondary")}>
          Apply
        </button>
        {hasProductFilters(filters) && (
          <Link href={listPath} className="text-sm text-secondary underline-offset-4 hover:underline">
            Reset filters
          </Link>
        )}
      </div>
    </form>
  );
}
