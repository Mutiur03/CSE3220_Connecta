import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";

vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));
vi.mock("next/navigation", () => ({ redirect: vi.fn() }));
vi.mock("@/lib/session", () => ({ getAccessToken: vi.fn() }));

import ProductStockForm from "./product-stock-form";

const noop = async () => ({});

const html = () => renderToStaticMarkup(<ProductStockForm action={noop} />);

describe("ProductStockForm", () => {
  it("offers receive, issue and adjust, with quantity and an optional reason", () => {
    const markup = html();
    expect(markup).toContain('<option value="RECEIVE" selected="">Receive</option>');
    expect(markup).toContain('<option value="ISSUE">Issue</option>');
    expect(markup).toContain('<option value="ADJUST">Adjust</option>');
    expect(markup).toContain('name="quantity"');
    expect(markup).toContain('name="reason"');
    expect(markup).toContain('maxLength="255"');
  });

  it("explains that an adjustment sets the level, and marks the quantity as numeric", () => {
    const markup = html();
    expect(markup).toContain("For Adjust, enter the new stock level.");
    expect(markup).toContain('inputMode="numeric"');
  });

  it("has no role gate: the form renders for any member", () => {
    expect(html()).toContain("Record movement");
  });
});
