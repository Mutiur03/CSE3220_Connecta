import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import type { StockMovement } from "@/lib/inventory-api";
import { StockMovementTable } from "./stock-movement-table";

// The server renders in UTC; only LocalTime swaps in the reader's zone in the browser, so the table must use it.
vi.mock("./local-time", () => ({
  LocalTime: ({ iso, kind }: { iso: string; kind: string }) => (
    <time data-local={kind}>{iso}</time>
  ),
}));

const movement: StockMovement = {
  id: "m-1",
  productId: "p-1",
  productName: "Oat Latte Kit",
  type: "ISSUE",
  quantityDelta: -3,
  quantityAfter: 9,
  reason: null,
  createdBy: { id: "u-1", fullName: "Ana Rahman" },
  createdAt: "2026-11-20T09:30:00Z",
};

const props = {
  productHref: (id: string) => `/organizations/org-1/products/${id}`,
  productsHref: "/organizations/org-1/products",
};

describe("StockMovementTable", () => {
  it("shows an empty state with a next step when there are no movements", () => {
    const markup = renderToStaticMarkup(
      <StockMovementTable movements={[]} {...props} />,
    );
    expect(markup).toContain("No stock movements yet");
    expect(markup).toContain('href="/organizations/org-1/products"');
    expect(markup).not.toContain("<table");
  });

  it("shows type, signed change, stock after and who made it, with the product linked", () => {
    const markup = renderToStaticMarkup(
      <StockMovementTable movements={[movement]} {...props} />,
    );
    expect(markup).toContain("Issued");
    expect(markup).toContain("-3");
    expect(markup).toContain(">9<");
    expect(markup).toContain("Ana Rahman");
    expect(markup).toContain('href="/organizations/org-1/products/p-1"');
  });

  it("shows when it happened in the reader's local time", () => {
    const markup = renderToStaticMarkup(
      <StockMovementTable movements={[movement]} {...props} />,
    );
    expect(markup).toContain(
      '<time data-local="dateTime">2026-11-20T09:30:00Z</time>',
    );
  });

  it("shows a dash when the person who made the movement is gone", () => {
    const markup = renderToStaticMarkup(
      <StockMovementTable
        movements={[{ ...movement, createdBy: null }]}
        {...props}
      />,
    );
    expect(markup).toContain("—");
  });

  it("shows the reason under the type when one was given", () => {
    const markup = renderToStaticMarkup(
      <StockMovementTable
        movements={[{ ...movement, reason: "Damaged in transit" }]}
        {...props}
      />,
    );
    expect(markup).toContain("Damaged in transit");
  });
});
