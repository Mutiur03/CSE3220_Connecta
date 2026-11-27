// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import type { OrgActionState } from "@/lib/org-actions";
import type { ShareCandidate } from "@/lib/product-share-api";
import ProductShareDialog from "./product-share-dialog";

const shoe: ShareCandidate = {
  id: "p1",
  name: "Runner shoe",
  sku: "SHOE-1",
  unitPrice: 59.9,
  stockStatus: "IN_STOCK",
  imageUrl: "/api/organizations/o1/products/p1/images/i1/content",
};
const page = (content: ShareCandidate[]) => ({
  data: { content, page: 0, size: 20, totalElements: content.length },
});

let container: HTMLDivElement;
let root: Root;

beforeAll(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  // jsdom has no modal dialog support.
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute("open", "");
  };
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute("open");
  };
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

type Send = (previous: OrgActionState, formData: FormData) => Promise<OrgActionState>;

const dialog = () => document.querySelector("dialog")!;
const click = (el: Element) => act(async () => (el as HTMLElement).click());
const rowButton = () => dialog().querySelector<HTMLButtonElement>("ul button")!;
const textarea = () => dialog().querySelector("textarea")!;
const alertBox = () => dialog().querySelector('[role="alert"]');

async function mount(
  send: Send = async () => ({}),
  search: () => Promise<ReturnType<typeof page> | { message: string }> = async () =>
    page([shoe]),
) {
  const searchMock = vi.fn(search);
  const preview = vi.fn(async () => ({
    data: { productId: "p1", text: "Runner shoe, 59.90", imageUrl: shoe.imageUrl },
  }));
  container = document.createElement("div");
  document.body.append(container);
  root = createRoot(container);
  await act(async () =>
    root.render(
      <ProductShareDialog
        search={searchMock as never}
        preview={preview}
        send={send}
      />,
    ),
  );
  await click(container.querySelector('[aria-label="Share a product"]')!);
  return { search: searchMock, preview };
}

const submit = () =>
  act(async () => {
    dialog()
      .querySelector("form")!
      .dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
  });

const type = (el: HTMLInputElement | HTMLTextAreaElement, value: string) =>
  act(async () => {
    const proto = el instanceof HTMLInputElement ? HTMLInputElement : HTMLTextAreaElement;
    Object.getOwnPropertyDescriptor(proto.prototype, "value")!.set!.call(el, value);
    el.dispatchEvent(new Event("input", { bubbles: true }));
  });

describe("ProductShareDialog", () => {
  it("opens, searches on open and lists name, SKU, price, stock and thumbnail", async () => {
    const { search } = await mount();
    expect(dialog().hasAttribute("open")).toBe(true);
    expect(search).toHaveBeenCalledWith("");
    const list = dialog().querySelector("ul")!;
    expect(list.textContent).toContain("Runner shoe");
    expect(list.textContent).toContain("SHOE-1");
    expect(list.textContent).toContain("59.90");
    expect(list.textContent).toContain("In stock");
    expect(list.querySelector("img")?.getAttribute("src")).toBe(shoe.imageUrl);
  });

  it("searches with the typed query", async () => {
    const { search } = await mount();
    await type(dialog().querySelector('input[type="search"]')!, "boot");
    await act(async () => {
      dialog()
        .querySelector("form[role=search]")!
        .dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    });
    expect(search).toHaveBeenLastCalledWith("boot");
  });

  it("loads the preview into an editable textarea when a row is chosen", async () => {
    const { preview } = await mount();
    await click(rowButton());
    expect(preview).toHaveBeenCalledWith("p1");
    expect(textarea().value).toBe("Runner shoe, 59.90");
    expect(dialog().querySelector("img")?.getAttribute("src")).toBe(shoe.imageUrl);
  });

  it("sends the edited text and closes the dialog", async () => {
    const send = vi.fn<Send>(async () => ({}));
    await mount(send);
    await click(rowButton());
    await type(textarea(), "Edited text");
    await submit();
    expect(send).toHaveBeenCalledOnce();
    const data = send.mock.calls[0][1];
    expect(data.get("text")).toBe("Edited text");
    expect(data.get("productId")).toBe("p1");
    expect(dialog().hasAttribute("open")).toBe(false);
  });

  it("shows a failure as an alert, keeps the text and stays open", async () => {
    await mount(async () => ({ message: "The reply window is closed." }));
    await click(rowButton());
    await submit();
    expect(dialog().hasAttribute("open")).toBe(true);
    expect(alertBox()?.textContent).toContain("window is closed");
    expect(textarea().value).toBe("Runner shoe, 59.90");
  });

  it("drops an old send failure after Back and choosing again", async () => {
    await mount(async () => ({ message: "The reply window is closed." }));
    await click(rowButton());
    await submit();
    expect(alertBox()).not.toBeNull();
    await click(
      [...dialog().querySelectorAll("button")].find((b) => b.textContent === "Back")!,
    );
    await click(rowButton());
    expect(alertBox()).toBeNull();
  });

  it("shows a search failure as an alert", async () => {
    await mount(async () => ({}), async () => ({ message: "Cannot reach the server." }));
    expect(alertBox()?.textContent).toContain("Cannot reach");
  });
});
