import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "./api";
import type { ProductImage } from "./product-image-api";

vi.mock("./product-image-api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("./product-image-api")>();
  return { ...actual, listProductImages: vi.fn() };
});

import { listProductImages } from "./product-image-api";
import { firstImageSources } from "./product-thumbnails";

const ORG = "00000000-0000-0000-0000-000000000001";
const WITH_IMAGES = "11111111-1111-1111-1111-111111111111";
const WITHOUT = "22222222-2222-2222-2222-222222222222";
const GONE = "33333333-3333-3333-3333-333333333333";

const image = (id: string, position: number): ProductImage => ({
  id,
  contentType: "image/png",
  sizeBytes: 1,
  position,
  contentPath: "",
  createdAt: "2026-03-04T09:30:00Z",
});

const listMock = vi.mocked(listProductImages);

beforeEach(() => {
  listMock.mockReset();
  listMock.mockImplementation(async (_token, _org, productId) => {
    if (productId === WITH_IMAGES) {
      return [image("later", 2), image("first", 0)];
    }
    if (productId === GONE) throw new ApiError(404, "gone");
    return [];
  });
});

describe("firstImageSources", () => {
  it("gives a product with images the proxy address of its first image by position", async () => {
    const sources = await firstImageSources("token", ORG, [{ id: WITH_IMAGES }]);
    expect(sources).toEqual({
      [WITH_IMAGES]: `/api/organizations/${ORG}/products/${WITH_IMAGES}/images/first/content`,
    });
  });

  it("passes no URL for a product without images", async () => {
    const sources = await firstImageSources("token", ORG, [{ id: WITHOUT }, { id: WITH_IMAGES }]);
    expect(Object.keys(sources)).toEqual([WITH_IMAGES]);
  });

  it("treats a product that disappeared mid-load as having no image", async () => {
    const sources = await firstImageSources("token", ORG, [{ id: GONE }]);
    expect(sources).toEqual({});
  });

  it("rethrows any other failure rather than hiding it", async () => {
    listMock.mockRejectedValueOnce(new ApiError(500, "boom"));
    await expect(firstImageSources("token", ORG, [{ id: WITH_IMAGES }])).rejects.toThrow("boom");
  });

  it("asks for each product's images once, and none when the page is empty", async () => {
    await firstImageSources("token", ORG, [{ id: WITH_IMAGES }, { id: WITHOUT }]);
    expect(listMock).toHaveBeenCalledTimes(2);
    await firstImageSources("token", ORG, []);
    expect(listMock).toHaveBeenCalledTimes(2);
  });
});
