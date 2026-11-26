"use server";

import { revalidatePath } from "next/cache";
import { actionErrorMessage } from "./org-errors";
import type { OrgActionState } from "./org-actions";
import { isSystemOwner } from "./org-permissions";
import { getAccessToken, getCurrentUser } from "./session";
import * as userApi from "./user-api";

const USERS_PATH = "/admin/users";
const NOT_ALLOWED = "Only the system owner can manage users.";

/** Disables or enables one account. The backend refuses owners (422); its message is shown as is. */
export async function setUserEnabled(
  userId: string,
  enabled: boolean,
): Promise<OrgActionState> {
  const user = await getCurrentUser(USERS_PATH);
  if (!isSystemOwner(user.roles)) return { message: NOT_ALLOWED };

  const accessToken = await getAccessToken(USERS_PATH);
  try {
    await userApi.setUserEnabled(accessToken, userId, enabled);
  } catch (error) {
    return { message: actionErrorMessage(error) };
  }
  revalidatePath(USERS_PATH);
  return {};
}
