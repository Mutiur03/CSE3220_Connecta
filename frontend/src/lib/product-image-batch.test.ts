import { describe, expect, it, vi } from "vitest";
import {
  IMAGE_LIMIT_MESSAGE,
  IMAGE_SIZE_MESSAGE,
  IMAGE_TYPE_MESSAGE,
  PRODUCT_IMAGE_LIMIT,
} from "./product-image-validation";
import { fileNamesLabel, uploadImageBatch, uploadSummary } from "./product-image-batch";

const png = (name: string) => new File(["x"], name, { type: "image/png" });
const okUpload = async () => ({ ok: true as const });

describe("uploadImageBatch", () => {
  it("sends each valid file once, in order, and reports it added", async () => {
    const upload = vi.fn(okUpload);
    const outcomes = await uploadImageBatch([png("a.png"), png("b.png")], { existing: 0, upload });
    expect(upload).toHaveBeenCalledTimes(2);
    expect(outcomes).toEqual([
      { name: "a.png", added: true },
      { name: "b.png", added: true },
    ]);
  });

  it("refuses a file of the wrong type or too large without sending it", async () => {
    const upload = vi.fn(okUpload);
    const gif = new File(["x"], "a.gif", { type: "image/gif" });
    const big = new File(["x"], "big.png", { type: "image/png" });
    Object.defineProperty(big, "size", { value: 5 * 1024 * 1024 + 1 });
    const outcomes = await uploadImageBatch([gif, big, png("ok.png")], { existing: 0, upload });
    expect(upload).toHaveBeenCalledTimes(1);
    expect(outcomes).toEqual([
      { name: "a.gif", added: false, reason: IMAGE_TYPE_MESSAGE },
      { name: "big.png", added: false, reason: IMAGE_SIZE_MESSAGE },
      { name: "ok.png", added: true },
    ]);
  });

  it("keeps the server's refusal reason for a file it rejects", async () => {
    const upload = async () => ({ ok: false as const, reason: "Choose a JPEG, PNG or WebP image." });
    const outcomes = await uploadImageBatch([png("a.png")], { existing: 0, upload });
    expect(outcomes).toEqual([{ name: "a.png", added: false, reason: "Choose a JPEG, PNG or WebP image." }]);
  });

  it("stops at the limit: files past it are refused with the limit message and not sent", async () => {
    const upload = vi.fn(okUpload);
    const files = [png("1.png"), png("2.png"), png("3.png")];
    const outcomes = await uploadImageBatch(files, { existing: PRODUCT_IMAGE_LIMIT - 2, upload });
    expect(upload).toHaveBeenCalledTimes(2);
    expect(outcomes[2]).toEqual({ name: "3.png", added: false, reason: IMAGE_LIMIT_MESSAGE });
  });

  it("refuses every file when the product is already full", async () => {
    const upload = vi.fn(okUpload);
    const outcomes = await uploadImageBatch([png("a.png")], { existing: PRODUCT_IMAGE_LIMIT, upload });
    expect(upload).not.toHaveBeenCalled();
    expect(outcomes[0].reason).toBe(IMAGE_LIMIT_MESSAGE);
  });

  it("reports progress before each upload", async () => {
    const onProgress = vi.fn();
    await uploadImageBatch([png("a.png"), png("b.png")], { existing: 0, upload: okUpload, onProgress });
    expect(onProgress.mock.calls).toEqual([
      [1, 2],
      [2, 2],
    ]);
  });
});

describe("uploadSummary", () => {
  it("reads plural for several files and singular for one", () => {
    expect(uploadSummary(3, 3)).toBe("3 images added.");
    expect(uploadSummary(2, 3)).toBe("2 of 3 images added.");
    expect(uploadSummary(1, 1)).toBe("Image added.");
    expect(uploadSummary(0, 1)).toBe("Image not added.");
  });
});

describe("fileNamesLabel", () => {
  it("lists the picked names, or says none are chosen", () => {
    expect(fileNamesLabel([])).toBe("No images chosen");
    expect(fileNamesLabel([png("a.png"), png("b.webp")])).toBe("a.png, b.webp");
  });
});
