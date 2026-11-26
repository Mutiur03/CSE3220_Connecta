"use server";

import { revalidatePath } from "next/cache";
import { assignConversation, setOwnAvailability } from "./assignment-api";
import { isAvailabilityStatus, UNASSIGNED } from "./assignment-view";
import { actionErrorMessage } from "./org-errors";
import type { OrgActionState } from "./org-actions";
import { isUuid } from "./org-validation";
import { getAccessToken } from "./session";

/** A member sets their own status. The inbox shows it and the team page counts it, so both refresh. */
export async function setAvailabilityAction(
  orgId: string,
  status: string,
): Promise<OrgActionState> {
  if (!isAvailabilityStatus(status)) return { message: "Unknown status." };
  const accessToken = await getAccessToken(`/organizations/${orgId}/inbox`);
  try {
    await setOwnAvailability(accessToken, orgId, status);
  } catch (error) {
    return { message: actionErrorMessage(error) };
  }
  revalidatePath(`/organizations/${orgId}`, "layout");
  return {};
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
