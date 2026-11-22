// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));
vi.mock("next/navigation", () => ({ redirect: vi.fn() }));
vi.mock("@/lib/session", () => ({ getAccessToken: vi.fn() }));

import ProductStockForm from "./product-stock-form";

const noop = async () => ({});

const html = () => renderToStaticMarkup(<ProductStockForm action={noop} />);

type Action = Parameters<typeof ProductStockForm>[0]["action"];

const mounted: { container: HTMLDivElement; root: Root }[] = [];
(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

/** Mounts the form in jsdom and returns its form and movement select. */
function mount(action: Action) {
  const container = document.createElement("div");
  document.body.appendChild(container);
  const root = createRoot(container);
  mounted.push({ container, root });
  act(() => root.render(<ProductStockForm action={action} />));
  return {
    form: container.querySelector("form") as HTMLFormElement,
    select: container.querySelector("select") as HTMLSelectElement,
  };
}

/** Picks an option the way a user does: set the value and fire the change event. */
function choose(select: HTMLSelectElement, type: string) {
  act(() => {
    select.value = type;
    select.dispatchEvent(new Event("change", { bubbles: true }));
  });
}

afterEach(() => {
  for (const { container, root } of mounted.splice(0)) {
    act(() => root.unmount());
    container.remove();
  }
});

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

  it("keeps the chosen movement type after a failed submit", async () => {
    const failed = vi.fn<Action>(async () => ({
      errors: { quantity: "Not enough stock." },
      values: { type: "ISSUE", quantity: "15", reason: "" },
    }));
    const { form, select } = mount(failed);

    choose(select, "ISSUE");
    await act(async () => form.requestSubmit());

    expect(failed).toHaveBeenCalledTimes(1);
    expect(select.value).toBe("ISSUE");
    expect(document.body.textContent).toContain("Not enough stock.");
  });
});
