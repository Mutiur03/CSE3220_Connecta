import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { Product } from "@/lib/product-api";
import ProductSummary from "./product-summary";

const product: Product = {
  id: "11111111-1111-1111-1111-111111111111",
  name: "Oat Latte Kit",
  description: "Six sachets\nOne box",
  categoryId: null,
  categoryName: null,
  sku: "OAT-6",
  unitPrice: 12.5,
  stockQuantity: 4,
  reorderLevel: 5,
  stockStatus: "LOW_STOCK",
  createdAt: "2026-01-02T00:00:00Z",
  updatedAt: "2026-01-02T00:00:00Z",
};

const html = (canManage: boolean) =>
  renderToStaticMarkup(<ProductSummary orgId="org-1" product={product} canManage={canManage} />);

describe("ProductSummary", () => {
  it("shows the SKU, price, stock on hand, reorder level and status", () => {
    const markup = html(false);
    expect(markup).toContain("OAT-6");
    expect(markup).toContain("12.50");
    expect(markup).toContain("Stock on hand");
    expect(markup).toContain("Reorder level");
    expect(markup).toContain("Low stock");
    expect(markup).toContain("Six sachets");
  });

  it("shows the edit link to an ADMIN only", () => {
    expect(html(true)).toContain('href="/organizations/org-1/products/11111111-1111-1111-1111-111111111111/edit"');
    expect(html(false)).not.toContain("Edit product");
  });
});
