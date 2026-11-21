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
  categoryName: null,
  createdAt: "2026-01-02T00:00:00Z",
  updatedAt: "2026-01-02T00:00:00Z",
};

const html = (canManage: boolean) =>
  renderToStaticMarkup(
    <ProductTable orgId="org-1" products={[product]} canManage={canManage} />,
  );

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

  it("shows a dash when the product has no category", () => {
    expect(html(false)).toContain("—");
  });
});
