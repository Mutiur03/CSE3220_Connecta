import { describe, expect, it } from "vitest";
import {
  IMAGE_LIMIT_MESSAGE,
  IMAGE_MISSING_MESSAGE,
  IMAGE_SIZE_MESSAGE,
  IMAGE_TYPE_MESSAGE,
  PRODUCT_IMAGE_LIMIT,
  PRODUCT_IMAGE_MAX_BYTES,
  imageCountText,
  imageUploadMessage,
  validateImageSelection,
  validateProductImage,
} from "./product-image-validation";

const MB = 1024 * 1024;

describe("validateProductImage", () => {
  it("accepts JPEG, PNG and WebP up to 5 MB", () => {
    for (const type of ["image/jpeg", "image/png", "image/webp"]) {
      expect(validateProductImage({ type, size: 1000 })).toBeUndefined();
    }
    expect(validateProductImage({ type: "image/png", size: PRODUCT_IMAGE_MAX_BYTES })).toBeUndefined();
  });

  it("rejects other types, including GIF and SVG", () => {
    expect(validateProductImage({ type: "image/gif", size: 1000 })).toBe(IMAGE_TYPE_MESSAGE);
    expect(validateProductImage({ type: "image/svg+xml", size: 1000 })).toBe(IMAGE_TYPE_MESSAGE);
  });

  it("rejects a file over 5 MB with a clear message", () => {
    expect(validateProductImage({ type: "image/jpeg", size: 5 * MB + 1 })).toBe(IMAGE_SIZE_MESSAGE);
  });
});

describe("validateImageSelection", () => {
  it("asks for a file when none or an empty one is picked", () => {
    expect(validateImageSelection(null)).toBe(IMAGE_MISSING_MESSAGE);
    expect(validateImageSelection("not a file")).toBe(IMAGE_MISSING_MESSAGE);
    expect(validateImageSelection(new File([], "a.png", { type: "image/png" }))).toBe(
      IMAGE_MISSING_MESSAGE,
    );
  });

  it("checks a picked file's type", () => {
    const file = new File(["x"], "a.gif", { type: "image/gif" });
    expect(validateImageSelection(file)).toBe(IMAGE_TYPE_MESSAGE);
  });
});

describe("imageUploadMessage", () => {
  it("explains the backend's 413, 415 and 422 answers", () => {
    expect(imageUploadMessage(413, "x")).toBe(IMAGE_SIZE_MESSAGE);
    expect(imageUploadMessage(415, "x")).toBe(IMAGE_TYPE_MESSAGE);
    expect(imageUploadMessage(422, "x")).toBe(IMAGE_LIMIT_MESSAGE);
  });

  it("keeps the fallback for any other failure", () => {
    expect(imageUploadMessage(403, "Only admins can do that")).toBe("Only admins can do that");
  });
});

describe("imageCountText", () => {
  it("uses the singular for one image and the plural otherwise", () => {
    expect(imageCountText(1)).toBe(`1 of ${PRODUCT_IMAGE_LIMIT} image added`);
    expect(imageCountText(3)).toBe(`3 of ${PRODUCT_IMAGE_LIMIT} images added`);
    expect(imageCountText(PRODUCT_IMAGE_LIMIT)).toBe(`${PRODUCT_IMAGE_LIMIT} of ${PRODUCT_IMAGE_LIMIT} images added`);
  });
});
