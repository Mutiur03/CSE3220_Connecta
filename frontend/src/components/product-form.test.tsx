import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";

vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));
vi.mock("next/navigation", () => ({ redirect: vi.fn() }));
vi.mock("@/lib/session", () => ({ getAccessToken: vi.fn() }));

import ProductForm, { type ProductFormValues } from "./product-form";

const CATEGORY = { id: "11111111-1111-1111-1111-111111111111", name: "Beverages" };
const DEFAULTS: ProductFormValues = {
  name: "Oat Latte Kit",
  description: "Six sachets",
  sku: "OAT-6",
  unitPrice: "12.50",
  reorderLevel: "5",
  categoryId: CATEGORY.id,
};

const noop = async () => ({});

const html = (stockQuantity?: number) =>
  renderToStaticMarkup(
    <ProductForm
      action={noop}
      defaults={DEFAULTS}
      categories={[CATEGORY]}
      stockQuantity={stockQuantity}
      submitLabel="Save product"
      busyLabel="Saving…"
      cancelHref="/organizations/org-1/products"
    />,
  );

describe("ProductForm fields", () => {
  it("has SKU, unit price, reorder level and a category picker with their current values", () => {
    const markup = html(4);
    expect(markup).toMatch(/name="sku"[^>]*value="OAT-6"/);
    expect(markup).toMatch(/name="unitPrice"[^>]*value="12.50"/);
    expect(markup).toMatch(/name="reorderLevel"[^>]*value="5"/);
    expect(markup).toContain('<option value="">No category</option>');
    expect(markup).toMatch(new RegExp(`value="${CATEGORY.id}"[^>]*selected`));
  });

  it("gives the new fields autocomplete and input-mode hints", () => {
    const markup = html();
    expect(markup).toMatch(/autoComplete="off" id="product-sku"/);
    expect(markup).toMatch(/inputMode="decimal" autoComplete="off" id="product-price"/);
    expect(markup).toMatch(/inputMode="numeric" autoComplete="off" id="product-reorder"/);
  });

  it("shows stock quantity read only on the edit form, not on the create form", () => {
    expect(html(4)).toContain("Stock quantity");
    expect(html(4)).toContain("recorded on the product&#x27;s detail page");
    expect(html()).not.toContain("Stock quantity");
    expect(html()).not.toMatch(/name="stockQuantity"/);
  });

  it("does not ask for stock quantity as an input", () => {
    expect(html(4)).not.toMatch(/<input[^>]*name="stockQuantity"/);
  });
});
