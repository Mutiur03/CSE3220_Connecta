import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "./api";

const api = vi.hoisted(() => ({ revokeSession: vi.fn(), revokeOtherSessions: vi.fn() }));
const revalidatePath = vi.hoisted(() => vi.fn());
vi.mock("./session-api", () => api);
vi.mock("./session", () => ({ getAccessToken: vi.fn().mockResolvedValue("token") }));
vi.mock("next/cache", () => ({ revalidatePath }));

import { revokeOtherSessions, revokeSession } from "./session-actions";

beforeEach(() => vi.clearAllMocks());

describe("revokeSession", () => {
  it("revokes with the caller's token and refreshes the settings page", async () => {
    api.revokeSession.mockResolvedValue(undefined);
    expect(await revokeSession("s1")).toEqual({});
    expect(api.revokeSession).toHaveBeenCalledWith("token", "s1");
    expect(revalidatePath).toHaveBeenCalledWith("/settings");
  });

  it("treats an already-gone session (404) as success", async () => {
    api.revokeSession.mockRejectedValue(new ApiError(404, "gone"));
    expect(await revokeSession("s1")).toEqual({});
    expect(revalidatePath).toHaveBeenCalled();
  });

  it("returns the message for any other failure without refreshing", async () => {
    api.revokeSession.mockRejectedValue(new ApiError(500, "boom"));
    expect(await revokeSession("s1")).toEqual({ message: "Something went wrong. Please try again." });
    expect(revalidatePath).not.toHaveBeenCalled();
  });
});

describe("revokeOtherSessions", () => {
  it("reports an unreachable server", async () => {
    api.revokeOtherSessions.mockRejectedValue(new Error("network"));
    expect(await revokeOtherSessions()).toEqual({ message: "Cannot reach the server. Please try again." });
  });
});
