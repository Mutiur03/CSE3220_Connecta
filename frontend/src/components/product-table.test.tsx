import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import type { Product } from "@/lib/product-api";

vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));
vi.mock("next/navigation", () => ({ redirect: vi.fn() }));
vi.mock("@/lib/session", () => ({ getAccessToken: vi.fn() }));

import ProductTable from "./product-table";

const product: Product = {
  id: "11111111-1111-1111-1111-111111111111",
  name: "Oat Latte Kit",
  description: "Six sachets",
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

const html = (canManage: boolean, rows: Product[] = [product]) =>
  renderToStaticMarkup(
    <ProductTable orgId="org-1" products={rows} canManage={canManage} />,
  );

describe("ProductTable columns", () => {
  it("shows the name, SKU, category, price and stock in their columns", () => {
    const markup = html(false);
    for (const heading of ["Name", "SKU", "Category", "Unit price", "Stock", "Status"]) {
      expect(markup).toContain(`>${heading}</th>`);
    }
    expect(markup).toContain("OAT-6");
    expect(markup).toContain(">12.50</td>");
    expect(markup).toContain(">4</td>");
  });

  it("formats the price with two decimals even for a whole amount", () => {
    expect(html(false, [{ ...product, unitPrice: 3 }])).toContain(">3.00</td>");
  });

  it("shows a dash for a missing SKU or category", () => {
    const markup = html(false, [{ ...product, sku: null }]);
    expect(markup.match(/—/g)).toHaveLength(2);
  });

  it("shows the low-stock and out-of-stock status badges", () => {
    expect(html(false)).toContain("Low stock");
    expect(html(false, [{ ...product, stockQuantity: 0, stockStatus: "OUT_OF_STOCK" }])).toContain(
      "Out of stock",
    );
  });
});

describe("ProductTable role gating", () => {
  it("shows edit and delete controls to an ADMIN", () => {
    const markup = html(true);
    expect(markup).toContain('aria-label="Edit Oat Latte Kit"');
    expect(markup).toContain('aria-label="Delete Oat Latte Kit"');
  });

  it("shows no edit or delete controls to an AGENT, only the read-only rows", () => {
    const markup = html(false);
    expect(markup).not.toContain("Edit Oat Latte Kit");
    expect(markup).not.toContain("Delete Oat Latte Kit");
    expect(markup).toContain("Oat Latte Kit");
  });
});
