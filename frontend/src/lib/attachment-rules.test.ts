import { describe, expect, it } from "vitest";
import {
  ATTACHMENT_ACCEPT,
  ATTACHMENT_MAX_BYTES,
  attachmentErrorMessage,
  attachmentProblem,
  formatFileSize,
} from "./attachment-rules";

describe("attachmentProblem", () => {
  it("accepts every allowed type up to 8 MB", () => {
    for (const type of ATTACHMENT_ACCEPT.split(",")) {
      expect(attachmentProblem({ type, size: ATTACHMENT_MAX_BYTES })).toBe(
        undefined,
      );
    }
  });

  it("rejects an empty file, an unknown type and a file over 8 MB", () => {
    expect(attachmentProblem({ type: "image/png", size: 0 })).toBe(
      "This file is empty.",
    );
    expect(attachmentProblem({ type: "image/webp", size: 10 })).toBe(
      "This file type is not supported.",
    );
    expect(attachmentProblem({ type: "", size: 10 })).toBe(
      "This file type is not supported.",
    );
    expect(
      attachmentProblem({ type: "video/mp4", size: ATTACHMENT_MAX_BYTES + 1 }),
    ).toBe("This file is larger than 8 MB.");
  });
});

describe("attachmentErrorMessage", () => {
  it("puts 413, 415 and 502 in plain words", () => {
    expect(attachmentErrorMessage(413, "x")).toBe(
      "This file is larger than 8 MB.",
    );
    expect(attachmentErrorMessage(415, "x")).toBe(
      "This file type is not supported.",
    );
    expect(attachmentErrorMessage(502, "x")).toBe(
      "Meta could not deliver the file. Try again.",
    );
  });

  it("keeps the backend's wording for a 422 and anything else", () => {
    expect(attachmentErrorMessage(422, "The reply window is closed.")).toBe(
      "The reply window is closed.",
    );
    expect(attachmentErrorMessage(undefined, "Cannot reach the server.")).toBe(
      "Cannot reach the server.",
    );
  });
});

describe("formatFileSize", () => {
  it("uses B, KB and one decimal of MB", () => {
    expect(formatFileSize(830)).toBe("830 B");
    expect(formatFileSize(12 * 1024)).toBe("12 KB");
    expect(formatFileSize(2.4 * 1024 * 1024)).toBe("2.4 MB");
  });
});
