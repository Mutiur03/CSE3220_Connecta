// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import AttachmentChip from "./attachment-chip";

(
  globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
let root: Root;
const createObjectURL = vi.fn(() => "blob:thumb");
const revokeObjectURL = vi.fn();

beforeEach(() => {
  Object.assign(URL, { createObjectURL, revokeObjectURL });
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
  vi.clearAllMocks();
});

const file = (name: string, type: string, size: number) =>
  new File([new Uint8Array(size)], name, { type });

describe("AttachmentChip", () => {
  it("shows an image as a thumbnail and revokes its URL on unmount", () => {
    act(() =>
      root.render(
        <AttachmentChip
          file={file("receipt.png", "image/png", 2048)}
          onRemove={() => {}}
        />,
      ),
    );
    expect(container.querySelector("img")?.getAttribute("src")).toBe(
      "blob:thumb",
    );
    expect(container.textContent).toContain("receipt.png");
    expect(container.textContent).toContain("2 KB");
    act(() => root.render(<></>));
    expect(revokeObjectURL).toHaveBeenCalledWith("blob:thumb");
  });

  it("shows a file icon for a PDF and removes through a named button", () => {
    const onRemove = vi.fn();
    act(() =>
      root.render(
        <AttachmentChip
          file={file("invoice.pdf", "application/pdf", 10)}
          onRemove={onRemove}
        />,
      ),
    );
    expect(container.querySelector("img")).toBeNull();
    expect(createObjectURL).not.toHaveBeenCalled();
    act(() =>
      (
        container.querySelector(
          'button[aria-label="Remove invoice.pdf"]',
        ) as HTMLButtonElement
      ).click(),
    );
    expect(onRemove).toHaveBeenCalled();
  });
});
