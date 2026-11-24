"use server";

import { revalidatePath } from "next/cache";
import {
  CONVERSATION_STATUSES,
  updateConversationStatus,
  type ConversationStatus,
} from "./inbox-api";
import { actionErrorMessage } from "./org-errors";
import type { OrgActionState } from "./org-actions";
import { getAccessToken } from "./session";

const threadPath = (orgId: string, conversationId: string) =>
  `/organizations/${orgId}/inbox/${conversationId}`;

/** Closes or reopens a conversation. The thread and the list both show the status, so both refresh. */
export async function setConversationStatusAction(
  orgId: string,
  conversationId: string,
  status: ConversationStatus,
): Promise<OrgActionState> {
  if (!CONVERSATION_STATUSES.includes(status)) {
    return { message: "Unknown status." };
  }
  const path = threadPath(orgId, conversationId);
  const accessToken = await getAccessToken(path);
  try {
    await updateConversationStatus(accessToken, orgId, conversationId, status);
  } catch (error) {
    return { message: actionErrorMessage(error) };
  }
  revalidatePath(path);
  revalidatePath(`/organizations/${orgId}/inbox`);
  return {};
}
