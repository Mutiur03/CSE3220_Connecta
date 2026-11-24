"use server";

import { revalidatePath } from "next/cache";
import { ApiError } from "./api";
import { actionErrorMessage } from "./org-errors";
import type { OrgActionState } from "./org-actions";
import { changePassword as apiChangePassword } from "./password-api";
import { getAccessToken } from "./session";
import { validatePasswordChange } from "./validation";

const SETTINGS_PAGE = "/settings";
const UNPROCESSABLE = 422;

// The backend's 422 detail texts. Each belongs on one field, so it shows there and not in the alert.
const WRONG_CURRENT = "The current password is incorrect";
const UNCHANGED = "The new password must differ from the current one";

const field = (formData: FormData, name: string) =>
  String(formData.get(name) ?? "");

/** Changes the signed-in user's password. The backend signs their other devices out. */
export async function changePassword(
  _previous: OrgActionState,
  formData: FormData,
): Promise<OrgActionState> {
  const current = field(formData, "currentPassword");
  const next = field(formData, "newPassword");
  const confirm = field(formData, "confirmPassword");

  const errors = validatePasswordChange(current, next, confirm);
  if (Object.keys(errors).length > 0) return { errors };

  const accessToken = await getAccessToken(SETTINGS_PAGE);
  try {
    await apiChangePassword(accessToken, current, next);
  } catch (error) {
    if (error instanceof ApiError && error.status === UNPROCESSABLE) {
      if (error.message === WRONG_CURRENT)
        return { errors: { currentPassword: `${error.message}.` } };
      if (error.message === UNCHANGED)
        return { errors: { newPassword: `${error.message}.` } };
    }
    return { message: actionErrorMessage(error) };
  }
  // The devices list below the form lost every other session.
  revalidatePath(SETTINGS_PAGE);
  return { notice: "Password changed. Your other devices were signed out." };
}
