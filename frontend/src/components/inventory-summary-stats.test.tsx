import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { InventorySummaryStats } from "./inventory-summary-stats";

const links = {
  products: "/organizations/org-1/products",
  lowStock: "/organizations/org-1/products?stockStatus=LOW_STOCK",
  outOfStock: "/organizations/org-1/products?stockStatus=OUT_OF_STOCK",
};

const summary = {
  productCount: 12,
  totalUnits: 1240,
  stockValue: 8431.5,
  lowStockCount: 3,
  outOfStockCount: 2,
};

const html = () =>
  renderToStaticMarkup(
    <InventorySummaryStats summary={summary} links={links} />,
  );

describe("InventorySummaryStats", () => {
  it("formats the counts and the stock value", () => {
    const markup = html();
    expect(markup).toContain("1,240");
    expect(markup).toContain("8431.50");
    expect(markup).toContain(">12<");
  });

  it("links the product count to the full list and the alerts to their filters", () => {
    const markup = html();
    expect(markup).toContain(`href="${links.products}"`);
    expect(markup).toContain(`href="${links.lowStock}"`);
    expect(markup).toContain(`href="${links.outOfStock}"`);
  });

  it("leaves units in stock and stock value without a link", () => {
    const markup = html();
    expect(markup.match(/<a /g)).toHaveLength(3);
    expect(markup).toContain("Units in stock");
    expect(markup).toContain("Stock value");
  });

  it("marks out of stock in the danger colour only when something is out", () => {
    expect(html()).toContain("text-danger");
    const none = renderToStaticMarkup(
      <InventorySummaryStats
        summary={{ ...summary, outOfStockCount: 0 }}
        links={links}
      />,
    );
    expect(none).not.toContain("text-danger");
  });
});
