import { ApiError } from "./api";
import type { Product } from "./product-api";
import { listProductImages, productImageSrc, type ProductImage } from "./product-image-api";

const isNotFound = (error: unknown) => error instanceof ApiError && error.status === 404;

/** A product deleted while the page loads has no images to show, so its 404 counts as none. */
const noImagesIfGone = (error: unknown): ProductImage[] => {
  if (isNotFound(error)) return [];
  throw error;
};

const firstByPosition = (images: ProductImage[]) =>
  images.reduce((first, image) => (image.position < first.position ? image : first));

/**
 * The proxy address of each product's first image, keyed by product id. A product without images
 * has no entry, so the list shows a placeholder for it. One small request per product, run
 * together; the list holds at most 100 products (MAX_PAGE_SIZE), so that is at most 100 requests.
 */
export async function firstImageSources(
  accessToken: string,
  orgId: string,
  products: Pick<Product, "id">[],
): Promise<Record<string, string>> {
  const lists = await Promise.all(
    products.map((product) =>
      listProductImages(accessToken, orgId, product.id).catch(noImagesIfGone),
    ),
  );
  const sources: Record<string, string> = {};
  products.forEach((product, index) => {
    const images = lists[index];
    if (images.length > 0) {
      sources[product.id] = productImageSrc(orgId, product.id, firstByPosition(images).id);
    }
  });
  return sources;
}
