import { beforeEach, describe, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({ changePassword: vi.fn() }));
const session = vi.hoisted(() => ({ getAccessToken: vi.fn() }));
const revalidatePath = vi.hoisted(() => vi.fn());

vi.mock("next/cache", () => ({ revalidatePath }));
vi.mock("./session", () => session);
vi.mock("./password-api", () => api);

import { ApiError } from "./api";
import { changePassword } from "./password-actions";

function form(current: string, next: string, confirm: string) {
  const data = new FormData();
  data.set("currentPassword", current);
  data.set("newPassword", next);
  data.set("confirmPassword", confirm);
  return data;
}

const VALID = form("old-password", "new-password-1", "new-password-1");

beforeEach(() => {
  vi.clearAllMocks();
  session.getAccessToken.mockResolvedValue("token");
});

describe("changePassword", () => {
  it("returns field errors without calling the backend", async () => {
    const state = await changePassword({}, form("", "short", "other"));

    expect(state.errors).toBeDefined();
    expect(api.changePassword).not.toHaveBeenCalled();
  });

  it("changes the password and says the other devices were signed out", async () => {
    api.changePassword.mockResolvedValue(undefined);

    const state = await changePassword({}, VALID);

    expect(api.changePassword).toHaveBeenCalledWith(
      "token",
      "old-password",
      "new-password-1",
    );
    expect(revalidatePath).toHaveBeenCalledWith("/settings");
    expect(state.notice).toBe(
      "Password changed. Your other devices were signed out.",
    );
  });

  it("shows a wrong current password on its field", async () => {
    api.changePassword.mockRejectedValue(
      new ApiError(422, "The current password is incorrect"),
    );

    const state = await changePassword({}, VALID);

    expect(state.errors?.currentPassword).toBe(
      "The current password is incorrect.",
    );
    expect(state.message).toBeUndefined();
  });

  it("shows an unchanged password on the new password field", async () => {
    api.changePassword.mockRejectedValue(
      new ApiError(422, "The new password must differ from the current one"),
    );

    const state = await changePassword({}, VALID);

    expect(state.errors?.newPassword).toBeDefined();
  });

  it("reports other failures in the alert line, never echoing a password", async () => {
    api.changePassword.mockRejectedValue(new ApiError(500, "boom"));

    const state = await changePassword({}, VALID);

    expect(state.message).toBe("Something went wrong. Please try again.");
    expect(JSON.stringify(state)).not.toContain("new-password-1");
  });
});
