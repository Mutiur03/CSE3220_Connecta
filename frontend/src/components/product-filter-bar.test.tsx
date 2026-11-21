import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import ProductFilterBar from "./product-filter-bar";

const CATEGORY = { id: "11111111-1111-1111-1111-111111111111", name: "Beverages" };
const LIST = "/organizations/org-1/products";

const html = (filters: Parameters<typeof ProductFilterBar>[0]["filters"]) =>
  renderToStaticMarkup(
    <ProductFilterBar listPath={LIST} filters={filters} categories={[CATEGORY]} />,
  );

describe("ProductFilterBar", () => {
  it("submits as a GET form to the list, so the filters live in the URL", () => {
    const markup = html({});
    expect(markup).toContain('method="get"');
    expect(markup).toContain(`action="${LIST}"`);
    expect(markup).toContain('name="q"');
    expect(markup).toContain('name="categoryId"');
    expect(markup).toContain('name="stockStatus"');
  });

  it("offers every category and every stock status as options", () => {
    const markup = html({});
    expect(markup).toContain(`value="${CATEGORY.id}"`);
    expect(markup).toContain("Beverages");
    for (const label of ["In stock", "Low stock", "Out of stock"]) {
      expect(markup).toContain(`>${label}</option>`);
    }
  });

  it("selects the category and stock status the URL asks for", () => {
    const markup = html({ categoryId: CATEGORY.id, stockStatus: "LOW_STOCK" });
    expect(markup).toMatch(new RegExp(`value="${CATEGORY.id}"[^>]*selected`));
    expect(markup).toMatch(/value="LOW_STOCK"[^>]*selected/);
  });

  it("keeps the search text in the box", () => {
    expect(html({ q: "oat milk" })).toMatch(/name="q"[^>]*value="oat milk"/);
  });

  it("shows a clear control for the search and a reset only when a filter is set", () => {
    expect(html({})).not.toContain('aria-label="Clear search"');
    expect(html({})).not.toContain("Reset filters");

    const filtered = html({ q: "oat" });
    expect(filtered).toContain('aria-label="Clear search"');
    expect(filtered).toContain("Reset filters");
  });

  it("clears only the search when the clear control is used, keeping the other filters", () => {
    const markup = html({ q: "oat", stockStatus: "OUT_OF_STOCK" });
    expect(markup).toContain('href="/organizations/org-1/products?stockStatus=OUT_OF_STOCK"');
    expect(markup).not.toContain("q=oat");
  });
});
