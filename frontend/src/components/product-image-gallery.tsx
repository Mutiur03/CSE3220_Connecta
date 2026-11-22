"use client";

import Image from "next/image";
import { useState } from "react";
import {
  deleteProductImage,
  uploadProductImage,
} from "@/lib/product-image-actions";
import type { ProductImage } from "@/lib/product-image-api";
import { productImageSrc } from "@/lib/product-image-api";
import { imageCountText } from "@/lib/product-image-validation";
import OrgActionButton from "./org-action-button";
import ProductImagePlaceholder from "./product-image-placeholder";
import ProductImageUploadForm from "./product-image-upload-form";
import ProductImageViewer from "./product-image-viewer";

type Props = {
  orgId: string;
  productId: string;
  productName: string;
  images: ProductImage[];
  /** Only an ADMIN uploads and removes images. Everyone sees the gallery. */
  canManage: boolean;
};

/**
 * The product's images as a grid of equal thumbnails in display order, each labelled with its
 * position. A thumbnail opens the full-size viewer. Thumbnails load through the app's proxy route,
 * so the access token never reaches the browser.
 */
export default function ProductImageGallery({
  orgId,
  productId,
  productName,
  images,
  canManage,
}: Props) {
  const [viewing, setViewing] = useState<number | null>(null);
  const viewerImages = images.map((image) => ({
    id: image.id,
    src: productImageSrc(orgId, productId, image.id),
  }));

  return (
    <div className="flex flex-col gap-6">
      {images.length === 0 ? (
        <div className="flex items-start gap-4">
          <ProductImagePlaceholder size="lg" />
          <div className="flex flex-col items-start gap-2">
            <p className="font-medium">No images yet</p>
            <p className="text-secondary">
              {canManage
                ? "Add a photo so members can recognise this product."
                : "An admin of this organization adds photos here."}
            </p>
          </div>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          <p className="text-secondary tabular-nums">
            {imageCountText(images.length)}
          </p>
          <ul className="grid grid-cols-[repeat(auto-fill,7.5rem)] gap-3">
            {viewerImages.map((image, index) => {
              const position = index + 1;
              return (
                <li key={image.id} className="flex flex-col gap-2">
                  <button
                    type="button"
                    onClick={() => setViewing(index)}
                    aria-label={`Open ${productName}, image ${position} of ${images.length}`}
                    className="relative aspect-square w-full overflow-hidden rounded-control border border-line bg-hover"
                  >
                    <Image
                      src={image.src}
                      alt={`${productName}, image ${position}`}
                      fill
                      unoptimized
                      sizes="120px"
                      className="object-cover"
                    />
                  </button>
                  <p className="text-sm text-secondary tabular-nums">
                    Image {position}
                  </p>
                  {canManage && (
                    <OrgActionButton
                      action={deleteProductImage.bind(
                        null,
                        orgId,
                        productId,
                        images[index].id,
                      )}
                      label="Remove"
                      pendingLabel="Removing…"
                      ariaLabel={`Remove image ${position} of ${productName}`}
                      confirm={`Remove image ${position} of ${productName}? This cannot be undone.`}
                      tone="danger"
                    />
                  )}
                </li>
              );
            })}
          </ul>
          <ProductImageViewer
            productName={productName}
            images={viewerImages}
            index={viewing}
            onIndexChange={setViewing}
            onClose={() => setViewing(null)}
          />
        </div>
      )}
      {canManage && (
        <ProductImageUploadForm
          action={uploadProductImage.bind(null, orgId, productId)}
          existing={images.length}
        />
      )}
    </div>
  );
}
