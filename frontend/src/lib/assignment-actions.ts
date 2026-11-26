"use server";

import { revalidatePath } from "next/cache";
import { assignConversation, setAssignable } from "./assignment-api";
import { UNASSIGNED } from "./assignment-view";
import { actionErrorMessage } from "./org-errors";
import type { OrgActionState } from "./org-actions";
import { isUuid } from "./org-validation";
import { getAccessToken } from "./session";

/** An admin lets auto-assignment pick a member, or stops it. The team page and the assignee lists show it. */
export async function setAssignableAction(
  orgId: string,
  membershipId: string,
  _previous: OrgActionState,
  formData: FormData,
): Promise<OrgActionState> {
  if (!isUuid(membershipId)) return { message: "Unknown member." };
  const assignable = formData.get("assignable") !== null;
  const accessToken = await getAccessToken(`/organizations/${orgId}/team`);
  try {
    await setAssignable(accessToken, orgId, membershipId, assignable);
  } catch (error) {
    return { message: actionErrorMessage(error) };
  }
  revalidatePath(`/organizations/${orgId}`, "layout");
  return { notice: assignable ? "Assignable." : "Not assignable." };
}

/** Assigns, reassigns or (empty choice) unassigns. A 404 or 422 from the backend shows as the form alert. */
export async function assignConversationAction(
  orgId: string,
  conversationId: string,
  _previous: OrgActionState,
  formData: FormData,
): Promise<OrgActionState> {
  const choice = String(formData.get("membershipId") ?? UNASSIGNED).trim();
  if (choice !== UNASSIGNED && !isUuid(choice)) {
    return { errors: { membershipId: "Choose a member from the list." } };
  }
  const path = `/organizations/${orgId}/inbox/${conversationId}`;
  const accessToken = await getAccessToken(path);
  try {
    await assignConversation(
      accessToken,
      orgId,
      conversationId,
      choice === UNASSIGNED ? null : choice,
    );
  } catch (error) {
    return { message: actionErrorMessage(error) };
  }
  revalidatePath(path);
  revalidatePath(`/organizations/${orgId}/team`);
  return { notice: choice === UNASSIGNED ? "Unassigned." : "Assignee saved." };
}
