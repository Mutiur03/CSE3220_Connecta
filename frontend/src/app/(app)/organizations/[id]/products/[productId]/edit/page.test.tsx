import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import type { OrgRole } from "@/lib/org-permissions";

const ORG = "00000000-0000-0000-0000-000000000001";
const PRODUCT = "11111111-1111-1111-1111-111111111111";
const IMAGE = "44444444-4444-4444-4444-444444444444";

const state = vi.hoisted(() => ({ role: "ADMIN" as OrgRole }));

vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));
vi.mock("next/navigation", () => ({
  notFound: vi.fn(() => {
    throw new Error("NOT_FOUND");
  }),
  redirect: vi.fn((href: string) => {
    throw new Error(`REDIRECT:${href}`);
  }),
}));
vi.mock("@/lib/session", () => ({ getAccessToken: vi.fn() }));
vi.mock("@/lib/product-actions", () => ({ updateProduct: vi.fn() }));
vi.mock("@/lib/product-image-actions", () => ({
  uploadProductImage: vi.fn(),
  deleteProductImage: vi.fn(),
}));
vi.mock("../../../data", () => ({
  loadOrganization: vi.fn(async () => ({ id: ORG, name: "Harbour Coffee", callerRole: state.role })),
  loadSession: vi.fn(async () => ({ accessToken: "token" })),
  notFoundOnMissing: <T,>(request: Promise<T>) => request,
}));
vi.mock("@/lib/product-api", () => ({
  getProduct: vi.fn(async () => ({
    id: PRODUCT,
    name: "Oat Latte Kit",
    description: null,
    categoryId: null,
    categoryName: null,
    sku: "OAT-6",
    unitPrice: 12.5,
    stockQuantity: 4,
    reorderLevel: 5,
    stockStatus: "LOW_STOCK",
    createdAt: "2026-01-02T00:00:00Z",
    updatedAt: "2026-01-02T00:00:00Z",
  })),
  listCategoryOptions: vi.fn(async () => []),
}));
vi.mock("@/lib/product-image-api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/product-image-api")>();
  return {
    ...actual,
    listProductImages: vi.fn(async () => [
      {
        id: IMAGE,
        contentType: "image/png",
        sizeBytes: 2048,
        position: 0,
        contentPath: "",
        createdAt: "2026-03-04T09:30:00Z",
      },
    ]),
  };
});

import EditProductPage from "./page";

const render = async () => {
  const element = await EditProductPage({
    params: Promise.resolve({ id: ORG, productId: PRODUCT }),
  });
  return renderToStaticMarkup(element);
};

describe("product edit page images", () => {
  it("renders the Images section with the gallery and upload controls for an ADMIN", async () => {
    state.role = "ADMIN";
    const markup = await render();
    expect(markup).toContain(">Images</h2>");
    expect(markup).toContain("1 of 8 image added");
    expect(markup).not.toContain("1 of 8 images.");
    expect(markup).toContain('alt="Oat Latte Kit, image 1"');
    expect(markup).toContain('aria-label="Remove image 1 of Oat Latte Kit"');
    expect(markup).toContain('type="file"');
  });

  it("sends an AGENT away from the edit page, so it never shows the gallery there", async () => {
    state.role = "AGENT";
    await expect(render()).rejects.toThrow(`REDIRECT:/organizations/${ORG}/products`);
  });
});
