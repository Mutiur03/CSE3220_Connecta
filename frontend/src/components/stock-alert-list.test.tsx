import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { Product } from "@/lib/product-api";
import { StockAlertList } from "./stock-alert-list";

const product: Product = {
  id: "p-1",
  name: "Oat Latte Kit",
  description: null,
  categoryId: null,
  categoryName: null,
  sku: "OAT-6",
  unitPrice: 12.5,
  stockQuantity: 0,
  reorderLevel: 5,
  stockStatus: "OUT_OF_STOCK",
  createdAt: "2026-01-02T00:00:00Z",
  updatedAt: "2026-01-02T00:00:00Z",
};

const base = {
  headingId: "out-heading",
  title: "Out of stock",
  viewAllHref: "/organizations/org-1/products?stockStatus=OUT_OF_STOCK",
  productHref: (id: string) => `/organizations/org-1/products/${id}`,
};

describe("StockAlertList", () => {
  it("shows the empty text when no product is in this status", () => {
    const markup = renderToStaticMarkup(
      <StockAlertList {...base} products={[]} total={0} emptyText="No products are out of stock." />,
    );
    expect(markup).toContain("No products are out of stock.");
    expect(markup).not.toContain("View all");
  });

  it("links each product and the full filtered list when there are more than shown", () => {
    const markup = renderToStaticMarkup(
      <StockAlertList {...base} products={[product]} total={14} emptyText="none" />,
    );
    expect(markup).toContain('href="/organizations/org-1/products/p-1"');
    expect(markup).toContain("View all 14");
    expect(markup).toContain(`href="${base.viewAllHref}"`);
  });
});
