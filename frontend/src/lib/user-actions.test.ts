import { beforeEach, describe, expect, it, vi } from "vitest";

const revalidatePath = vi.hoisted(() => vi.fn());
const api = vi.hoisted(() => ({ setUserEnabled: vi.fn() }));
const session = vi.hoisted(() => ({
  getAccessToken: vi.fn(),
  getCurrentUser: vi.fn(),
}));

vi.mock("next/cache", () => ({ revalidatePath }));
vi.mock("./session", () => session);
vi.mock("./user-api", () => api);

import { ApiError } from "./api";
import { setUserEnabled } from "./user-actions";

beforeEach(() => {
  vi.clearAllMocks();
  session.getAccessToken.mockResolvedValue("access");
  session.getCurrentUser.mockResolvedValue({ roles: ["OWNER"] });
});

describe("setUserEnabled", () => {
  it("calls the API with the bearer token and refreshes the list", async () => {
    api.setUserEnabled.mockResolvedValue({});
    await expect(setUserEnabled("u1", false)).resolves.toEqual({});
    expect(api.setUserEnabled).toHaveBeenCalledWith("access", "u1", false);
    expect(revalidatePath).toHaveBeenCalledWith("/admin/users");
  });

  it("refuses a non-owner without calling the API", async () => {
    session.getCurrentUser.mockResolvedValue({ roles: ["ADMIN"] });
    const state = await setUserEnabled("u1", false);
    expect(state.message).toBeDefined();
    expect(api.setUserEnabled).not.toHaveBeenCalled();
  });

  it("shows the backend's message for a 422", async () => {
    api.setUserEnabled.mockRejectedValue(
      new ApiError(422, "An owner cannot be disabled"),
    );
    const state = await setUserEnabled("u1", false);
    expect(state.message).toBe("An owner cannot be disabled");
    expect(revalidatePath).not.toHaveBeenCalled();
  });

  it("maps a server error to a generic message", async () => {
    api.setUserEnabled.mockRejectedValue(new ApiError(500, "boom"));
    expect((await setUserEnabled("u1", true)).message).toBe(
      "Something went wrong. Please try again.",
    );
  });
});
