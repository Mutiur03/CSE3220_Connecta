import type { OrgActionState } from "./org-actions";

/** The first field error in the order the action returned them, if any. */
export function firstFieldError(
  errors: OrgActionState["errors"],
): string | undefined {
  return Object.values(errors ?? {}).find((message): message is string =>
    Boolean(message),
  );
}

/** What a form's alert line says: the action's own failure, else its first field error. */
export function alertText(state: OrgActionState): string | undefined {
  return state.message || firstFieldError(state.errors);
}
