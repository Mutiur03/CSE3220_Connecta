import { beforeEach, describe, expect, it, vi } from "vitest";

const revalidatePath = vi.hoisted(() => vi.fn());
const api = vi.hoisted(() => ({
  assignConversation: vi.fn(),
  setAssignable: vi.fn(),
}));
const session = vi.hoisted(() => ({ getAccessToken: vi.fn() }));

vi.mock("next/cache", () => ({ revalidatePath }));
vi.mock("./session", () => session);
vi.mock("./assignment-api", () => api);

import { ApiError } from "./api";
import {
  assignConversationAction,
  setAssignableAction,
} from "./assignment-actions";

const ORG = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";
const CONV = "3f2504e0-4f89-11d3-9a0c-0305e82c3302";
const MEMBER = "3f2504e0-4f89-11d3-9a0c-0305e82c3303";

const form = (membershipId: string) => {
  const data = new FormData();
  data.set("membershipId", membershipId);
  return data;
};

beforeEach(() => {
  vi.clearAllMocks();
  session.getAccessToken.mockResolvedValue("access");
});

describe("assignConversationAction", () => {
  it("rejects a malformed id on the field without calling the API", async () => {
    const state = await assignConversationAction(ORG, CONV, {}, form("nope"));
    expect(state.errors?.membershipId).toBeDefined();
    expect(api.assignConversation).not.toHaveBeenCalled();
  });

  it("assigns a member and refreshes the thread and the team page", async () => {
    api.assignConversation.mockResolvedValue({});
    const state = await assignConversationAction(ORG, CONV, {}, form(MEMBER));
    expect(api.assignConversation).toHaveBeenCalledWith(
      "access",
      ORG,
      CONV,
      MEMBER,
    );
    expect(state.notice).toBe("Assignee saved.");
    expect(revalidatePath).toHaveBeenCalledWith(`/organizations/${ORG}/team`);
  });

  it("unassigns with null when the choice is empty", async () => {
    api.assignConversation.mockResolvedValue({});
    const state = await assignConversationAction(ORG, CONV, {}, form(""));
    expect(api.assignConversation).toHaveBeenCalledWith(
      "access",
      ORG,
      CONV,
      null,
    );
    expect(state.notice).toBe("Unassigned.");
  });

  it("shows a 422 from the backend as the form message", async () => {
    api.assignConversation.mockRejectedValue(
      new ApiError(422, "That member is not available."),
    );
    const state = await assignConversationAction(ORG, CONV, {}, form(MEMBER));
    expect(state.message).toBe("That member is not available.");
    expect(revalidatePath).not.toHaveBeenCalled();
  });
});

describe("setAssignableAction", () => {
  const switchForm = (on: boolean) => {
    const data = new FormData();
    if (on) data.set("assignable", "on");
    return data;
  };

  it("rejects a malformed member id without calling the API", async () => {
    const state = await setAssignableAction(ORG, "x", {}, switchForm(true));
    expect(state.message).toBeDefined();
    expect(api.setAssignable).not.toHaveBeenCalled();
  });

  it("saves true when the box is checked and refreshes the organization", async () => {
    api.setAssignable.mockResolvedValue({});
    const state = await setAssignableAction(ORG, MEMBER, {}, switchForm(true));
    expect(api.setAssignable).toHaveBeenCalledWith("access", ORG, MEMBER, true);
    expect(state.notice).toBe("Assignable.");
    expect(revalidatePath).toHaveBeenCalled();
  });

  it("saves false when the box is unchecked", async () => {
    api.setAssignable.mockResolvedValue({});
    const state = await setAssignableAction(ORG, MEMBER, {}, switchForm(false));
    expect(api.setAssignable).toHaveBeenCalledWith(
      "access",
      ORG,
      MEMBER,
      false,
    );
    expect(state.notice).toBe("Not assignable.");
  });

  it("returns the failure message and does not refresh", async () => {
    api.setAssignable.mockRejectedValue(new ApiError(404, "Not found."));
    const state = await setAssignableAction(ORG, MEMBER, {}, switchForm(true));
    expect(state.message).toBe("Not found.");
    expect(revalidatePath).not.toHaveBeenCalled();
  });
});
