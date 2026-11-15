import { describe, expect, it, vi } from "vitest";
import { copyText } from "./copy-text";

describe("copyText", () => {
  it("writes the text and reports success", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    await expect(copyText("abc", { writeText })).resolves.toBe(true);
    expect(writeText).toHaveBeenCalledWith("abc");
  });

  it("reports failure when the clipboard rejects the write", async () => {
    const writeText = vi.fn().mockRejectedValue(new Error("denied"));
    await expect(copyText("abc", { writeText })).resolves.toBe(false);
  });

  it("reports failure when there is no clipboard", async () => {
    await expect(copyText("abc", undefined)).resolves.toBe(false);
  });
});
