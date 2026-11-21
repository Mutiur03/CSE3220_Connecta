import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import type { ProductImage } from "@/lib/product-image-api";

vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));
vi.mock("next/navigation", () => ({ redirect: vi.fn() }));
vi.mock("@/lib/session", () => ({ getAccessToken: vi.fn() }));

import ProductImageGallery from "./product-image-gallery";

const ORG = "00000000-0000-0000-0000-000000000001";
const PRODUCT = "11111111-1111-1111-1111-111111111111";
const IMAGE = "44444444-4444-4444-4444-444444444444";

const image: ProductImage = {
  id: IMAGE,
  contentType: "image/png",
  sizeBytes: 2048,
  position: 0,
  contentPath: `/api/organizations/${ORG}/products/${PRODUCT}/images/${IMAGE}/content`,
  createdAt: "2026-03-04T09:30:00Z",
};

const html = (canManage: boolean, images: ProductImage[] = [image]) =>
  renderToStaticMarkup(
    <ProductImageGallery
      orgId={ORG}
      productId={PRODUCT}
      productName="Oat Latte Kit"
      images={images}
      canManage={canManage}
    />,
  );

describe("ProductImageGallery", () => {
  it("shows each image through the same-origin proxy route, with alt text from name and position", () => {
    const markup = html(false);
    expect(markup).toContain(`src="/api/organizations/${ORG}/products/${PRODUCT}/images/${IMAGE}/content"`);
    expect(markup).toContain('alt="Oat Latte Kit, image 1"');
  });

  it("never puts the access token or a bearer header in the page", () => {
    const markup = html(true);
    expect(markup).not.toContain("Bearer");
    expect(markup).not.toContain("access_token");
  });

  it("shows upload and remove controls to an ADMIN", () => {
    const markup = html(true);
    expect(markup).toContain('type="file"');
    expect(markup).toContain('accept="image/jpeg,image/png,image/webp"');
    expect(markup).toContain('aria-label="Remove image 1 of Oat Latte Kit"');
  });

  it("hides upload and remove from an AGENT", () => {
    const markup = html(false);
    expect(markup).not.toContain('type="file"');
    expect(markup).not.toContain("Remove image 1");
    expect(markup).not.toContain("Upload image");
  });

  it("explains an empty gallery, with the next action for an ADMIN", () => {
    expect(html(true, [])).toContain("No images yet");
    expect(html(true, [])).toContain("Add a photo so members can recognise this product.");
    expect(html(false, [])).toContain("An admin of this organization adds photos here.");
  });
});
