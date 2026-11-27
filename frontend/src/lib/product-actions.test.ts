import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "./api";

const api = vi.hoisted(() => ({ createProduct: vi.fn(), updateProduct: vi.fn(), deleteProduct: vi.fn() }));
const redirect = vi.hoisted(() => vi.fn());
vi.mock("./product-api", () => api);
vi.mock("./session", () => ({ getAccessToken: vi.fn().mockResolvedValue("token") }));
vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));
vi.mock("next/navigation", () => ({ redirect }));

import { createProduct, deleteProduct, updateProduct } from "./product-actions";

const form = (fields: Record<string, string>) => {
  const data = new FormData();
  for (const [key, value] of Object.entries(fields)) data.set(key, value);
  return data;
};
const valid = { name: "Mug", unitPrice: "9.99", reorderLevel: "5" };

beforeEach(() => vi.clearAllMocks());

describe("createProduct", () => {
  it("returns field errors and the typed values without calling the backend", async () => {
    const state = await createProduct("o", {}, form({ name: " ", unitPrice: "abc", reorderLevel: "5" }));
    expect(state.errors?.name).toBeTruthy();
    expect(state.errors?.unitPrice).toBeTruthy();
    expect(state.values?.reorderLevel).toBe("5");
    expect(api.createProduct).not.toHaveBeenCalled();
  });

  it("sends blank optional fields as null and redirects to the list", async () => {
    api.createProduct.mockResolvedValue({});
    await createProduct("o", {}, form(valid));
    expect(api.createProduct).toHaveBeenCalledWith("token", "o", expect.objectContaining({
      name: "Mug",
      description: null,
      sku: null,
      categoryId: null,
    }));
    expect(redirect).toHaveBeenCalledWith("/organizations/o/products");
  });

  it("keeps the form values and shows the backend message when the call fails", async () => {
    api.createProduct.mockRejectedValue(new ApiError(409, "SKU already used"));
    const state = await createProduct("o", {}, form(valid));
    expect(state.message).toBe("SKU already used");
    expect(state.values?.name).toBe("Mug");
    expect(redirect).not.toHaveBeenCalled();
  });
});

describe("updateProduct", () => {
  it("fails with a message and no redirect on a server error", async () => {
    api.updateProduct.mockRejectedValue(new ApiError(500, "boom"));
    const state = await updateProduct("o", "p", {}, form(valid));
    expect(state.message).toBe("Something went wrong. Please try again.");
    expect(redirect).not.toHaveBeenCalled();
  });
});

describe("deleteProduct", () => {
  it("returns an empty state on success", async () => {
    api.deleteProduct.mockResolvedValue(undefined);
    expect(await deleteProduct("o", "p")).toEqual({});
  });

  it("returns the failure message", async () => {
    api.deleteProduct.mockRejectedValue(new ApiError(403, "Not allowed"));
    expect(await deleteProduct("o", "p")).toEqual({ message: "Not allowed" });
  });
});
