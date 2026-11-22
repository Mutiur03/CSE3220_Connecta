"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { actionErrorMessage } from "./org-errors";
import type { OrgActionState } from "./org-actions";
import * as metaApi from "./meta-api";
import { getAccessToken } from "./session";
import { parsePageIds } from "./meta-view";

const integrationsPath = (orgId: string) =>
  `/organizations/${orgId}/integrations`;

/**
 * Starts the Facebook login. On success the browser is sent to Meta; a failure comes back as a message on the
 * button's line. redirect() throws to navigate, so it stays outside the try block.
 */
export async function startFacebookConnection(
  orgId: string,
): Promise<OrgActionState> {
  const accessToken = await getAccessToken(integrationsPath(orgId));
  let authorizationUrl: string;
  try {
    ({ authorizationUrl } = await metaApi.startMetaConnection(
      accessToken,
      orgId,
    ));
  } catch (error) {
    return { message: actionErrorMessage(error) };
  }
  redirect(authorizationUrl);
}

/** Starts Instagram Business Login. Same contract as {@link startFacebookConnection}. */
export async function startInstagramConnection(
  orgId: string,
): Promise<OrgActionState> {
  const accessToken = await getAccessToken(integrationsPath(orgId));
  let authorizationUrl: string;
  try {
    ({ authorizationUrl } = await metaApi.startInstagramConnection(
      accessToken,
      orgId,
    ));
  } catch (error) {
    return { message: actionErrorMessage(error) };
  }
  redirect(authorizationUrl);
}

/** Connects the Pages the admin ticked. The backend subscribes each one before it is saved. */
export async function connectPagesAction(
  orgId: string,
  _previous: OrgActionState,
  formData: FormData,
): Promise<OrgActionState> {
  const pageIds = parsePageIds(formData.getAll("pageId"));
  if (pageIds.length === 0)
    return { message: "Choose at least one Page to connect." };

  const accessToken = await getAccessToken(integrationsPath(orgId));
  try {
    const connected = await metaApi.connectPages(accessToken, orgId, pageIds);
    revalidatePath(integrationsPath(orgId));
    return {
      notice:
        connected.length === 1
          ? "1 Page connected."
          : `${connected.length} Pages connected.`,
    };
  } catch (error) {
    return { message: actionErrorMessage(error) };
  }
}
