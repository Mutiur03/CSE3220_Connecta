// Client-side checks that mirror the image rules on the backend. The backend validates again and
// answers 413, 415 or 422 for the same failures; imageUploadMessage turns those into plain text.
export const PRODUCT_IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"] as const;
export const PRODUCT_IMAGE_MAX_BYTES = 5 * 1024 * 1024;
/** The backend keeps at most this many images per product. */
export const PRODUCT_IMAGE_LIMIT = 8;

export const IMAGE_TYPE_MESSAGE = "Choose a JPEG, PNG or WebP image.";
export const IMAGE_SIZE_MESSAGE = "Choose an image of 5 MB or less.";
export const IMAGE_MISSING_MESSAGE = "Choose an image to upload.";
export const IMAGE_LIMIT_MESSAGE = `The limit of ${PRODUCT_IMAGE_LIMIT} images is reached. Remove one to add more.`;
export const IMAGE_UPLOAD_FAILED_MESSAGE = "The image could not be added. Try again.";

/** The problem with a picked file, or undefined when it can be uploaded. */
export function validateProductImage(file: { type: string; size: number }): string | undefined {
  if (!(PRODUCT_IMAGE_TYPES as readonly string[]).includes(file.type)) return IMAGE_TYPE_MESSAGE;
  if (file.size > PRODUCT_IMAGE_MAX_BYTES) return IMAGE_SIZE_MESSAGE;
  return undefined;
}

/** A form value is a picked file or nothing; an empty file input sends a blank file part. */
export function validateImageSelection(value: FormDataEntryValue | null): string | undefined {
  if (!(value instanceof File) || value.size === 0) return IMAGE_MISSING_MESSAGE;
  return validateProductImage(value);
}

/** The alert text for a failed upload, by the status the backend answered with. */
export function imageUploadMessage(status: number | undefined, fallback: string): string {
  if (status === 413) return IMAGE_SIZE_MESSAGE;
  if (status === 415) return IMAGE_TYPE_MESSAGE;
  if (status === 422) return IMAGE_LIMIT_MESSAGE;
  return fallback;
}

/** "1 of 8 image added" or "3 of 8 images added". Empty galleries say so in their own copy. */
export function imageCountText(count: number): string {
  const noun = count === 1 ? "image" : "images";
  return `${count} of ${PRODUCT_IMAGE_LIMIT} ${noun} added`;
}
