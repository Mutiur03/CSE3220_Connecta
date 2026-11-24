import { beforeEach, describe, expect, it, vi } from "vitest";

const revalidatePath = vi.hoisted(() => vi.fn());
const api = vi.hoisted(() => ({
  updateConversationStatus: vi.fn(),
  sendMessage: vi.fn(),
  MESSAGE_MAX_LENGTH: 1000,
  CONVERSATION_STATUSES: ["OPEN", "CLOSED"],
}));
vi.mock("next/cache", () => ({ revalidatePath }));
vi.mock("./session", () => ({
  getAccessToken: vi.fn().mockResolvedValue("tok"),
}));
vi.mock("./inbox-api", () => api);

import { ApiError } from "./api";
import {
  sendMessageAction,
  setConversationStatusAction,
} from "./inbox-actions";

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

describe("sendMessageAction", () => {
  const form = (text: string) => {
    const data = new FormData();
    data.set("text", text);
    return data;
  };
  const send = (text: string) => sendMessageAction("o1", "c1", {}, form(text));

  it("sends the trimmed text and refreshes the thread and the list", async () => {
    api.sendMessage.mockResolvedValue({});
    expect(await send("  hi  ")).toEqual({});
    expect(api.sendMessage).toHaveBeenCalledWith("tok", "o1", "c1", "hi");
    expect(revalidatePath).toHaveBeenCalledWith("/organizations/o1/inbox/c1");
    expect(revalidatePath).toHaveBeenCalledWith("/organizations/o1/inbox");
  });

  it("rejects blank and too long text on the field without calling the API", async () => {
    expect((await send("   ")).errors?.text).toBeDefined();
    expect((await send("x".repeat(1001))).errors?.text).toBeDefined();
    expect(api.sendMessage).not.toHaveBeenCalled();
  });

  it.each([
    [422, "The reply window is closed."],
    [400, "bad text"],
    [502, "Meta could not deliver the message. Try again."],
  ])(
    "shows the backend detail for %i and keeps the text",
    async (status, detail) => {
      api.sendMessage.mockRejectedValue(new ApiError(status, detail));
      expect(await send("hi")).toEqual({
        message: detail,
        values: { text: "hi" },
      });
      expect(revalidatePath).not.toHaveBeenCalled();
    },
  );

  it("uses the generic wording for other server errors", async () => {
    api.sendMessage.mockRejectedValue(new ApiError(500, "boom"));
    expect((await send("hi")).message).not.toContain("boom");
  });
});
