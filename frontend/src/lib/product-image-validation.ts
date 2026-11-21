// Client-side checks that mirror the image rules on the backend. The backend validates again and
// answers 413, 415 or 422 for the same failures; imageUploadMessage turns those into plain text.
export const PRODUCT_IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"] as const;
export const PRODUCT_IMAGE_MAX_BYTES = 5 * 1024 * 1024;
/** The backend keeps at most this many images per product. */
export const PRODUCT_IMAGE_LIMIT = 8;

export const IMAGE_TYPE_MESSAGE = "Choose a JPEG, PNG or WebP image.";
export const IMAGE_SIZE_MESSAGE = "Choose an image of 5 MB or less.";
export const IMAGE_MISSING_MESSAGE = "Choose an image to upload.";
const IMAGE_LIMIT_MESSAGE = `This product already has the most images (${PRODUCT_IMAGE_LIMIT}). Remove one to add another.`;

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
