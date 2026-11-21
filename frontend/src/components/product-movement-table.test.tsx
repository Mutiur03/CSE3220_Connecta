import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { StockMovement } from "@/lib/stock-api";
import ProductMovementTable, { formatDelta } from "./product-movement-table";

const movement: StockMovement = {
  id: "22222222-2222-2222-2222-222222222222",
  productId: "11111111-1111-1111-1111-111111111111",
  productName: "Oat Latte Kit",
  type: "ISSUE",
  quantityDelta: -3,
  quantityAfter: 9,
  reason: "Café order",
  createdBy: { id: "33333333-3333-3333-3333-333333333333", fullName: "Aavas Roy" },
  createdAt: "2026-03-04T09:30:00Z",
};

const html = (movements: StockMovement[]) =>
  renderToStaticMarkup(<ProductMovementTable movements={movements} />);

describe("formatDelta", () => {
  it("signs stock in and stock out, and shows an unchanged adjustment as 0", () => {
    expect(formatDelta(5)).toBe("+5");
    expect(formatDelta(-3)).toBe("−3");
    expect(formatDelta(0)).toBe("0");
  });
});

describe("ProductMovementTable", () => {
  it("shows each movement with its type, change, level after, reason and author", () => {
    const markup = html([movement]);
    for (const heading of ["Type", "Change", "On hand after", "Reason", "Recorded by", "When"]) {
      expect(markup).toContain(`>${heading}</th>`);
    }
    expect(markup).toContain(">Issue</td>");
    expect(markup).toContain(">−3</td>");
    expect(markup).toContain(">9</td>");
    expect(markup).toContain("Café order");
    expect(markup).toContain("Aavas Roy");
    expect(markup).toContain('dateTime="2026-03-04T09:30:00Z"');
  });

  it("shows a dash for a missing reason and a removed user for a missing author", () => {
    const markup = html([{ ...movement, reason: null, createdBy: null }]);
    expect(markup).toContain(">—</td>");
    expect(markup).toContain("Removed user");
  });

  it("keeps rows in the order it is given, which is newest first from the backend", () => {
    const newer = { ...movement, id: "a", reason: "Newer" };
    const older = { ...movement, id: "b", reason: "Older" };
    const markup = html([newer, older]);
    expect(markup.indexOf("Newer")).toBeLessThan(markup.indexOf("Older"));
  });

  it("shows an empty state with the next action when there are no movements", () => {
    const markup = html([]);
    expect(markup).toContain("No stock movements yet");
    expect(markup).toContain("Record a receipt, an issue or an adjustment above");
    expect(markup).not.toContain("<table");
  });
});
