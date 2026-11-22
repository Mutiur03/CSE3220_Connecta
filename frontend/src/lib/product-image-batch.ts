import {
  IMAGE_LIMIT_MESSAGE,
  PRODUCT_IMAGE_LIMIT,
  validateProductImage,
} from "./product-image-validation";

/** What the server said about one file: added, or the reason it was not. */
export type UploadResult = { ok: true } | { ok: false; reason: string };

export type FileOutcome = { name: string; added: boolean; reason?: string };

type BatchOptions = {
  /** Images the product already has, before this batch starts. */
  existing: number;
  /** Sends one file. Called only for files that passed the client checks and fit the limit. */
  upload: (file: File) => Promise<UploadResult>;
  /** Called before each upload with the 1-based position and the number of files in the batch. */
  onProgress?: (position: number, total: number) => void;
};

/**
 * Uploads the files one after another, so the server sees one image per request and enforces the
 * limit on each. A file that is the wrong type or size, or that would go past the limit, is not
 * sent: it is reported with its reason. Every file gets exactly one outcome, in the order picked.
 */
export async function uploadImageBatch(files: File[], options: BatchOptions): Promise<FileOutcome[]> {
  const outcomes: FileOutcome[] = [];
  let added = 0;
  for (const [index, file] of files.entries()) {
    const refusal = validateProductImage(file) ?? limitRefusal(options.existing + added);
    if (refusal) {
      outcomes.push({ name: file.name, added: false, reason: refusal });
      continue;
    }
    options.onProgress?.(index + 1, files.length);
    const result = await options.upload(file);
    if (result.ok) added += 1;
    outcomes.push(result.ok ? { name: file.name, added: true } : { name: file.name, added: false, reason: result.reason });
  }
  return outcomes;
}

function limitRefusal(imagesNow: number): string | undefined {
  return imagesNow >= PRODUCT_IMAGE_LIMIT ? IMAGE_LIMIT_MESSAGE : undefined;
}

/** "3 images added.", "2 of 3 images added." or "Image not added." for a single file. */
export function uploadSummary(added: number, total: number): string {
  if (total === 1) return added === 1 ? "Image added." : "Image not added.";
  if (added === total) return `${added} images added.`;
  return `${added} of ${total} images added.`;
}

/** The names of the picked files for the line beside the picker, or a prompt when none. */
export function fileNamesLabel(files: File[]): string {
  return files.length === 0 ? "No images chosen" : files.map((file) => file.name).join(", ");
}
