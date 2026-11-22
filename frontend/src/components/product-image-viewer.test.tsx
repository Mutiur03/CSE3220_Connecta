import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";

vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));
vi.mock("next/navigation", () => ({ redirect: vi.fn() }));
vi.mock("@/lib/session", () => ({ getAccessToken: vi.fn() }));

import ProductImageViewer, { stepIndex } from "./product-image-viewer";

const images = [
  { id: "a", src: "/api/images/a/content" },
  { id: "b", src: "/api/images/b/content" },
  { id: "c", src: "/api/images/c/content" },
];

const html = (index: number | null, count = images.length) =>
  renderToStaticMarkup(
    <ProductImageViewer
      productName="Oat Latte Kit"
      images={images.slice(0, count)}
      index={index}
      onIndexChange={() => undefined}
      onClose={() => undefined}
    />,
  );

describe("stepIndex", () => {
  it("moves forward and back inside the list", () => {
    expect(stepIndex(0, 1, 3)).toBe(1);
    expect(stepIndex(2, -1, 3)).toBe(1);
  });

  it("wraps: after the last image comes the first, and before the first the last", () => {
    expect(stepIndex(2, 1, 3)).toBe(0);
    expect(stepIndex(0, -1, 3)).toBe(2);
  });

  it("stays on the only image when there is one", () => {
    expect(stepIndex(0, 1, 1)).toBe(0);
    expect(stepIndex(0, -1, 1)).toBe(0);
  });
});

describe("ProductImageViewer", () => {
  it("is a named native dialog, with no role or aria-modal of its own", () => {
    const markup = html(0);
    expect(markup).toContain('<dialog');
    expect(markup).toContain('aria-label="Oat Latte Kit images"');
    expect(markup).not.toContain('aria-modal');
    expect(markup).not.toContain('role="dialog"');
  });

  it("shows the full image with alt text from the product name and position", () => {
    const markup = html(1);
    expect(markup).toContain('src="/api/images/b/content"');
    expect(markup).toContain('alt="Oat Latte Kit, image 2"');
  });

  it("captions the position as Image n of N", () => {
    expect(html(1)).toContain("Image 2 of 3");
  });

  it("offers previous and next buttons when there is more than one image", () => {
    const markup = html(0);
    expect(markup).toContain("Previous image");
    expect(markup).toContain("Next image");
  });

  it("hides previous and next for a single image", () => {
    const markup = html(0, 1);
    expect(markup).not.toContain("Previous image");
    expect(markup).not.toContain("Next image");
  });

  it("has a Close button that closes the dialog through a form", () => {
    expect(html(0)).toContain('method="dialog"');
  });

  it("renders no image content while closed", () => {
    expect(html(null)).not.toContain("Image 1 of 3");
    expect(html(null)).not.toContain("/content");
  });
});
