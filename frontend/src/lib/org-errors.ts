import { ApiError } from "./api";

const SESSION_EXPIRED =
  "Your session has expired. Reload the page to continue.";
const SERVER_ERROR = "Something went wrong. Please try again.";
const UNREACHABLE = "Cannot reach the server. Please try again.";

/**
 * The message a form shows for a failed backend call. A 4xx carries the backend's ProblemDetail
 * detail (for example "The last owner cannot leave"), which is written to be shown as is.
 */
export function actionErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) return UNREACHABLE;
  if (error.status === 401) return SESSION_EXPIRED;
  if (error.status >= 500) return SERVER_ERROR;
  return error.message;
}
