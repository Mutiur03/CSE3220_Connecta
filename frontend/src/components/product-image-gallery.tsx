import Image from "next/image";
import { deleteProductImage, uploadProductImage } from "@/lib/product-image-actions";
import type { ProductImage } from "@/lib/product-image-api";
import { productImageSrc } from "@/lib/product-image-api";
import OrgActionButton from "./org-action-button";
import ProductImageUploadForm from "./product-image-upload-form";

type Props = {
  orgId: string;
  productId: string;
  productName: string;
  images: ProductImage[];
  /** Only an ADMIN uploads and removes images. Everyone sees the gallery. */
  canManage: boolean;
};

/**
 * The product's images as a grid of thumbnails in display order. Thumbnails load through the
 * app's proxy route, so the access token never reaches the browser.
 */
export default function ProductImageGallery({ orgId, productId, productName, images, canManage }: Props) {
  return (
    <div className="flex flex-col gap-6">
      {images.length === 0 ? (
        <div className="flex flex-col items-start gap-2 border-t border-line-subtle pt-6">
          <p className="font-medium">No images yet</p>
          <p className="text-secondary">
            {canManage
              ? "Add a photo so members can recognise this product."
              : "An admin of this organization adds photos here."}
          </p>
        </div>
      ) : (
        <ul className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4">
          {images.map((image, index) => {
            const position = index + 1;
            return (
              <li key={image.id} className="flex flex-col gap-2">
                <div className="relative aspect-square overflow-hidden rounded-control border border-line bg-hover">
                  <Image
                    src={productImageSrc(orgId, productId, image.id)}
                    alt={`${productName}, image ${position}`}
                    fill
                    unoptimized
                    sizes="(min-width: 1024px) 25vw, (min-width: 640px) 33vw, 50vw"
                    className="object-cover"
                  />
                </div>
                <div className="flex items-center justify-between gap-2">
                  <p className="text-sm text-secondary tabular-nums">Image {position}</p>
                  {canManage && (
                    <OrgActionButton
                      action={deleteProductImage.bind(null, orgId, productId, image.id)}
                      label="Remove"
                      pendingLabel="Removing…"
                      ariaLabel={`Remove image ${position} of ${productName}`}
                      confirm={`Remove image ${position} of ${productName}? This cannot be undone.`}
                      tone="danger"
                      size="sm"
                    />
                  )}
                </div>
              </li>
            );
          })}
        </ul>
      )}
      {canManage && (
        <ProductImageUploadForm action={uploadProductImage.bind(null, orgId, productId)} />
      )}
    </div>
  );
}
