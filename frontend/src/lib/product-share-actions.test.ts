import { beforeEach, describe, expect, it, vi } from "vitest";

const revalidatePath = vi.hoisted(() => vi.fn());
const api = vi.hoisted(() => ({
  searchShareProducts: vi.fn(),
  previewProductShare: vi.fn(),
  sendProductShare: vi.fn(),
}));
vi.mock("next/cache", () => ({ revalidatePath }));
vi.mock("./session", () => ({
  getAccessToken: vi.fn().mockResolvedValue("tok"),
}));
vi.mock("./product-share-api", () => api);

import { ApiError } from "./api";
import {
  previewProductShareAction,
  searchShareProductsAction,
  sendProductShareAction,
} from "./product-share-actions";

beforeEach(() => vi.clearAllMocks());

const form = (fields: Record<string, string>) => {
  const data = new FormData();
  Object.entries(fields).forEach(([key, value]) => data.set(key, value));
  return data;
};

describe("searchShareProductsAction", () => {
  it("passes the trimmed query and returns the page", async () => {
    api.searchShareProducts.mockResolvedValue({ content: [] });
    expect(await searchShareProductsAction("o1", "c1", " shoe ")).toEqual({
      data: { content: [] },
    });
    expect(api.searchShareProducts).toHaveBeenCalledWith("tok", "o1", "c1", "shoe");
  });

  it("turns a failure into a message", async () => {
    api.searchShareProducts.mockRejectedValue(new ApiError(404, "gone"));
    expect(await searchShareProductsAction("o1", "c1", "")).toEqual({
      message: "gone",
    });
  });
});

describe("previewProductShareAction", () => {
  it("returns the preview", async () => {
    api.previewProductShare.mockResolvedValue({ text: "hi" });
    expect(await previewProductShareAction("o1", "c1", "p1")).toEqual({
      data: { text: "hi" },
    });
  });
});

describe("sendProductShareAction", () => {
  it("sends the edited text and refreshes the thread", async () => {
    api.sendProductShare.mockResolvedValue({ messages: [] });
    const result = await sendProductShareAction(
      "o1",
      "c1",
      {},
      form({ productId: "p1", text: " Edited " }),
    );
    expect(result).toEqual({});
    expect(api.sendProductShare).toHaveBeenCalledWith("tok", "o1", "c1", "p1", "Edited");
    expect(revalidatePath).toHaveBeenCalledWith("/organizations/o1/inbox/c1");
  });

  it("rejects empty and over-long text without calling the backend", async () => {
    const empty = await sendProductShareAction("o1", "c1", {}, form({ productId: "p1", text: " " }));
    expect(empty.errors?.text).toBeDefined();
    const long = await sendProductShareAction(
      "o1",
      "c1",
      {},
      form({ productId: "p1", text: "x".repeat(1001) }),
    );
    expect(long.errors?.text).toContain("1000");
    expect(api.sendProductShare).not.toHaveBeenCalled();
  });

  it.each([422, 502])("shows the backend wording for a %i and echoes the text", async (status) => {
    api.sendProductShare.mockRejectedValue(new ApiError(status, "Window closed"));
    expect(
      await sendProductShareAction("o1", "c1", {}, form({ productId: "p1", text: "hi" })),
    ).toEqual({ message: "Window closed", values: { text: "hi" } });
    expect(revalidatePath).not.toHaveBeenCalled();
  });
});
