import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "./api";

const api = vi.hoisted(() => ({ createCategory: vi.fn(), deleteCategory: vi.fn() }));
vi.mock("./category-api", () => api);
vi.mock("./session", () => ({ getAccessToken: vi.fn().mockResolvedValue("token") }));
vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));
vi.mock("next/navigation", () => ({ redirect: vi.fn() }));

import { createCategory, deleteCategory } from "./category-actions";

const form = (name: string, description = "") => {
  const data = new FormData();
  data.set("name", name);
  data.set("description", description);
  return data;
};

beforeEach(() => vi.clearAllMocks());

describe("createCategory", () => {
  it("returns field errors without calling the backend", async () => {
    const state = await createCategory("org", {}, form(""));
    expect(state.errors?.name).toBe("Enter a name for the category.");
    expect(api.createCategory).not.toHaveBeenCalled();
  });

  it("shows a duplicate name (409) on the name field, not in the alert", async () => {
    api.createCategory.mockRejectedValue(new ApiError(409, "Conflict"));
    const state = await createCategory("org", {}, form("Beverages"));
    expect(state.errors?.name).toBe("A category with this name already exists.");
    expect(state.message).toBeUndefined();
    expect(state.values?.name).toBe("Beverages");
  });

  it("shows another failure in the alert", async () => {
    api.createCategory.mockRejectedValue(new ApiError(500, "boom"));
    const state = await createCategory("org", {}, form("Beverages"));
    expect(state.message).toBe("Something went wrong. Please try again.");
    expect(state.errors).toBeUndefined();
  });
});

describe("deleteCategory", () => {
  it("returns the failure message", async () => {
    api.deleteCategory.mockRejectedValue(new ApiError(403, "Not allowed"));
    expect(await deleteCategory("org", "id")).toEqual({ message: "Not allowed" });
  });
});
