import { beforeEach, describe, expect, it, vi } from "vitest";

const revalidatePath = vi.hoisted(() => vi.fn());
const api = vi.hoisted(() => ({
  updateConversationStatus: vi.fn(),
  CONVERSATION_STATUSES: ["OPEN", "CLOSED"],
}));
vi.mock("next/cache", () => ({ revalidatePath }));
vi.mock("./session", () => ({
  getAccessToken: vi.fn().mockResolvedValue("tok"),
}));
vi.mock("./inbox-api", () => api);

import { ApiError } from "./api";
import { setConversationStatusAction } from "./inbox-actions";

beforeEach(() => vi.clearAllMocks());

describe("setConversationStatusAction", () => {
  it("patches the status and refreshes the thread and the list", async () => {
    api.updateConversationStatus.mockResolvedValue({});
    expect(await setConversationStatusAction("o1", "c1", "CLOSED")).toEqual({});
    expect(api.updateConversationStatus).toHaveBeenCalledWith(
      "tok",
      "o1",
      "c1",
      "CLOSED",
    );
    expect(revalidatePath).toHaveBeenCalledWith("/organizations/o1/inbox/c1");
    expect(revalidatePath).toHaveBeenCalledWith("/organizations/o1/inbox");
  });

  it("returns the backend message on failure", async () => {
    api.updateConversationStatus.mockRejectedValue(new ApiError(404, "gone"));
    expect(await setConversationStatusAction("o1", "c1", "OPEN")).toEqual({
      message: "gone",
    });
    expect(revalidatePath).not.toHaveBeenCalled();
  });

  it("rejects an unknown status without calling the API", async () => {
    const state = await setConversationStatusAction(
      "o1",
      "c1",
      "SNOOZED" as never,
    );
    expect(state.message).toBeDefined();
    expect(api.updateConversationStatus).not.toHaveBeenCalled();
  });
});
