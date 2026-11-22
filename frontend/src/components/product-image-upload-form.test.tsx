import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { PRODUCT_IMAGE_LIMIT } from "@/lib/product-image-validation";
import ProductImageUploadForm from "./product-image-upload-form";

const noop = async () => ({});

const html = (existing: number) =>
  renderToStaticMarkup(
    <ProductImageUploadForm action={noop} existing={existing} />,
  );

describe("ProductImageUploadForm", () => {
  it("accepts several JPEG, PNG and WebP files through a hidden, labelled input", () => {
    const markup = html(0);
    expect(markup).toContain('type="file"');
    expect(markup).toContain(" multiple");
    expect(markup).toContain('accept="image/jpeg,image/png,image/webp"');
    expect(markup).toContain('aria-label="Product images"');
    expect(markup).toContain("hidden");
  });

  it("shows a dashed add tile and no previews until files are chosen", () => {
    const markup = html(0);
    expect(markup).toContain("Add images");
    expect(markup).toContain("border-dashed");
    expect(markup).not.toContain("Preview of");
  });

  it("keeps the upload control enabled below the limit and shows no limit message", () => {
    const markup = html(PRODUCT_IMAGE_LIMIT - 1);
    expect(markup).not.toContain("The limit of 8 images is reached.");
    expect(markup).not.toContain('disabled=""');
  });

  it("disables upload at the limit and says how to make room", () => {
    const markup = html(PRODUCT_IMAGE_LIMIT);
    expect(markup).toContain(
      "The limit of 8 images is reached. Remove one to add more.",
    );
    expect(markup.match(/disabled=""/g)).toHaveLength(3);
  });

  it("keeps the status region for screen readers", () => {
    expect(html(0)).toContain('role="status"');
  });
});
