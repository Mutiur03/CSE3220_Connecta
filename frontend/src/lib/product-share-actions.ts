"use server";

import { revalidatePath } from "next/cache";
import { ApiError } from "./api";
import { MESSAGE_MAX_LENGTH } from "./inbox-api";
import type { OrgActionState } from "./org-actions";
import { actionErrorMessage } from "./org-errors";
import * as shareApi from "./product-share-api";
import { getAccessToken } from "./session";

const threadPath = (orgId: string, conversationId: string) =>
  `/organizations/${orgId}/inbox/${conversationId}`;

/** What the picker's search and preview calls hand back: the data, or a message for the dialog's alert. */
export type ShareLookup<T> = { data: T; message?: undefined } | { data?: undefined; message: string };

/** A 422 (channel not connected, window closed) and a 502 (Meta refused) carry wording written for the agent. */
const shareErrorMessage = (error: unknown) =>
  error instanceof ApiError && (error.status === 422 || error.status === 502)
    ? error.message
    : actionErrorMessage(error);

async function lookup<T>(
  orgId: string,
  conversationId: string,
  call: (accessToken: string) => Promise<T>,
): Promise<ShareLookup<T>> {
  const accessToken = await getAccessToken(threadPath(orgId, conversationId));
  try {
    return { data: await call(accessToken) };
  } catch (error) {
    return { message: actionErrorMessage(error) };
  }
}

export async function searchShareProductsAction(
  orgId: string,
  conversationId: string,
  query: string,
) {
  return lookup(orgId, conversationId, (token) =>
    shareApi.searchShareProducts(token, orgId, conversationId, query.trim()),
  );
}

export async function previewProductShareAction(
  orgId: string,
  conversationId: string,
  productId: string,
) {
  return lookup(orgId, conversationId, (token) =>
    shareApi.previewProductShare(token, orgId, conversationId, productId),
  );
}

/** Sends the (possibly edited) text and the product's image. On failure the text is echoed back. */
export async function sendProductShareAction(
  orgId: string,
  conversationId: string,
  _previous: OrgActionState,
  formData: FormData,
): Promise<OrgActionState> {
  const productId = String(formData.get("productId") ?? "");
  const text = String(formData.get("text") ?? "").trim();
  if (!productId) return { message: "Choose a product first." };
  if (!text)
    return { errors: { text: "Write a message first." }, values: { text } };
  if (text.length > MESSAGE_MAX_LENGTH)
    return {
      errors: { text: `Keep the message under ${MESSAGE_MAX_LENGTH} characters.` },
      values: { text },
    };
  const path = threadPath(orgId, conversationId);
  const accessToken = await getAccessToken(path);
  try {
    await shareApi.sendProductShare(accessToken, orgId, conversationId, productId, text);
  } catch (error) {
    return { message: shareErrorMessage(error), values: { text } };
  }
  revalidatePath(path);
  revalidatePath(`/organizations/${orgId}/inbox`);
  return {};
}
