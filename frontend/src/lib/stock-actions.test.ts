import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "./api";

const api = vi.hoisted(() => ({ recordStockMovement: vi.fn() }));
vi.mock("./stock-api", () => api);
vi.mock("./session", () => ({ getAccessToken: vi.fn().mockResolvedValue("token") }));
vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));

import { recordStockMovement } from "./stock-actions";

const form = (type: string, quantity: string, reason = "") => {
  const data = new FormData();
  data.set("type", type);
  data.set("quantity", quantity);
  data.set("reason", reason);
  return data;
};

beforeEach(() => vi.clearAllMocks());

describe("recordStockMovement", () => {
  it("rejects an unknown movement type without calling the backend", async () => {
    const state = await recordStockMovement("o", "p", {}, form("STEAL", "1"));
    expect(state.message).toBe("Choose receive, issue or adjust.");
    expect(api.recordStockMovement).not.toHaveBeenCalled();
  });

  it("returns a quantity error for zero units on ISSUE", async () => {
    const state = await recordStockMovement("o", "p", {}, form("ISSUE", "0"));
    expect(state.errors?.quantity).toBeTruthy();
    expect(api.recordStockMovement).not.toHaveBeenCalled();
  });

  it("allows ADJUST to zero and sends a null reason when blank", async () => {
    api.recordStockMovement.mockResolvedValue(undefined);
    const state = await recordStockMovement("o", "p", {}, form("ADJUST", "0"));
    expect(state.notice).toBe("Movement recorded.");
    expect(api.recordStockMovement).toHaveBeenCalledWith("token", "o", "p", {
      type: "ADJUST",
      quantity: 0,
      reason: null,
    });
  });

  it("shows an over-issue (422) on the quantity field", async () => {
    api.recordStockMovement.mockRejectedValue(new ApiError(422, "short"));
    const state = await recordStockMovement("o", "p", {}, form("ISSUE", "99"));
    expect(state.errors?.quantity).toContain("Not enough stock");
    expect(state.values?.quantity).toBe("99");
  });

  it("shows another failure in the alert", async () => {
    api.recordStockMovement.mockRejectedValue(new ApiError(403, "Not allowed"));
    const state = await recordStockMovement("o", "p", {}, form("RECEIVE", "5"));
    expect(state.message).toBe("Not allowed");
  });
});
