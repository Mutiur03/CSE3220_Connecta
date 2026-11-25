// Client-side checks that mirror the backend's attachment allow-list and size limit. The backend checks
// again and answers 413, 415, 422 or 502; attachmentErrorMessage turns those into plain words.
export const ATTACHMENT_TYPES = [
  "image/jpeg",
  "image/png",
  "image/gif",
  "video/mp4",
  "audio/mpeg",
  "audio/mp4",
  "audio/aac",
  "audio/wav",
  "application/pdf",
] as const;
export const ATTACHMENT_ACCEPT = ATTACHMENT_TYPES.join(",");
export const ATTACHMENT_MAX_BYTES = 8 * 1024 * 1024;

export const ATTACHMENT_SIZE_MESSAGE = "This file is larger than 8 MB.";
export const ATTACHMENT_TYPE_MESSAGE = "This file type is not supported.";
export const ATTACHMENT_EMPTY_MESSAGE = "This file is empty.";
export const ATTACHMENT_META_MESSAGE =
  "Meta could not deliver the file. Try again.";

/** The problem with a picked file, or undefined when it can be sent. */
export function attachmentProblem(file: {
  type: string;
  size: number;
}): string | undefined {
  if (file.size === 0) return ATTACHMENT_EMPTY_MESSAGE;
  if (!(ATTACHMENT_TYPES as readonly string[]).includes(file.type))
    return ATTACHMENT_TYPE_MESSAGE;
  if (file.size > ATTACHMENT_MAX_BYTES) return ATTACHMENT_SIZE_MESSAGE;
  return undefined;
}

/**
 * The alert text for a failed upload, by the backend's status. A 422 (reply window closed, channel not
 * connected, not supported on Instagram) keeps the backend's own wording, which `fallback` carries.
 */
export function attachmentErrorMessage(
  status: number | undefined,
  fallback: string,
): string {
  if (status === 413) return ATTACHMENT_SIZE_MESSAGE;
  if (status === 415) return ATTACHMENT_TYPE_MESSAGE;
  if (status === 502) return ATTACHMENT_META_MESSAGE;
  return fallback;
}

/** "830 B", "12 KB", "2.4 MB". */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export const isImage = (file: { type: string }) =>
  file.type.startsWith("image/");

export const isAudio = (file: { type: string }) =>
  file.type.startsWith("audio/");
