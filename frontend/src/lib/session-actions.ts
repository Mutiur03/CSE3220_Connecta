"use server";

import { revalidatePath } from "next/cache";
import { ApiError } from "./api";
import { actionErrorMessage } from "./org-errors";
import type { OrgActionState } from "./org-actions";
import { getAccessToken } from "./session";
import * as sessionApi from "./session-api";

const SESSIONS_PAGE = "/settings";

/** Runs one backend call; a failure becomes a message for the button's alert line. */
async function attempt(
  call: (accessToken: string) => Promise<void>,
): Promise<OrgActionState> {
  const accessToken = await getAccessToken(SESSIONS_PAGE);
  try {
    await call(accessToken);
  } catch (error) {
    // The session may already be gone (revoked elsewhere): the refreshed list shows the truth.
    if (!(error instanceof ApiError && error.status === 404)) {
      return { message: actionErrorMessage(error) };
    }
  }
  revalidatePath(SESSIONS_PAGE);
  return {};
}

/** Signs one device out. */
export async function revokeSession(sessionId: string): Promise<OrgActionState> {
  return attempt((token) => sessionApi.revokeSession(token, sessionId));
}

/** Signs out every device except the one making the request. */
export async function revokeOtherSessions(): Promise<OrgActionState> {
  return attempt((token) => sessionApi.revokeOtherSessions(token));
}
