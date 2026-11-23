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
export default function ProductFilterBar({
  listPath,
  filters,
  categories,
}: Props) {
  const clearSearchHref = productsHref(listPath, { ...filters, q: undefined });

  return (
    <form
      role="search"
      aria-label="Filter products"
      method="get"
      action={listPath}
      className="flex flex-wrap items-center gap-2"
    >
      {/* The labels are for screen readers: the placeholder and each "All ..." option already say
          what the control filters, so the row stays one line on a desktop. */}
      <div className="flex min-w-56 flex-[2_1_16rem] items-center gap-2">
        <label htmlFor="product-search" className="sr-only">
          Search
        </label>
        <input
          id="product-search"
          name="q"
          type="search"
          defaultValue={filters.q ?? ""}
          placeholder="Search by name or SKU"
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
      <div className="flex min-w-40 flex-[1_1_10rem]">
        <label htmlFor="product-category" className="sr-only">
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
      <div className="flex min-w-40 flex-[1_1_10rem]">
        <label htmlFor="product-stock" className="sr-only">
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
          <Link
            href={listPath}
            className="text-sm text-secondary underline-offset-4 hover:text-primary hover:underline"
          >
            Reset filters
          </Link>
        )}
      </div>
    </form>
  );
}
