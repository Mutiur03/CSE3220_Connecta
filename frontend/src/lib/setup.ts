import "server-only";

import { getSetupStatus } from "./api";

/**
 * True while the system has no owner, so /setup must run first. If the backend cannot be reached
 * this answers false: the sign-in and sign-up pages then show their own "cannot reach the server"
 * errors instead of sending visitors to a setup page that would fail too.
 */
export async function isSetupRequired(): Promise<boolean> {
  try {
    return (await getSetupStatus()).setupRequired;
  } catch {
    return false;
  }
}
