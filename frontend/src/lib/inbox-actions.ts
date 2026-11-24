"use server";

import { revalidatePath } from "next/cache";
import { ApiError } from "./api";
import {
  CONVERSATION_STATUSES,
  MESSAGE_MAX_LENGTH,
  sendMessage,
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

/** A failed send keeps its backend wording: a 502 ("Meta could not deliver ... Try again.") is safe to show. */
const sendErrorMessage = (error: unknown) =>
  error instanceof ApiError && error.status === 502
    ? error.message
    : actionErrorMessage(error);

/** Sends a reply. On failure the typed text is echoed back so the agent can retry. */
export async function sendMessageAction(
  orgId: string,
  conversationId: string,
  _previous: OrgActionState,
  formData: FormData,
): Promise<OrgActionState> {
  const text = String(formData.get("text") ?? "").trim();
  if (!text)
    return { errors: { text: "Write a message first." }, values: { text } };
  if (text.length > MESSAGE_MAX_LENGTH) {
    return {
      errors: {
        text: `Keep the message under ${MESSAGE_MAX_LENGTH} characters.`,
      },
      values: { text },
    };
  }
  const path = threadPath(orgId, conversationId);
  const accessToken = await getAccessToken(path);
  try {
    await sendMessage(accessToken, orgId, conversationId, text);
  } catch (error) {
    return { message: sendErrorMessage(error), values: { text } };
  }
  revalidatePath(path);
  revalidatePath(`/organizations/${orgId}/inbox`);
  return {};
}
