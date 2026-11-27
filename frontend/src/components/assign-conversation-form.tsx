"use client";

import { useActionState } from "react";
import type { MemberAvailability } from "@/lib/assignment-api";
import { memberOptionLabel, UNASSIGNED } from "@/lib/assignment-view";
import type { OrgActionState } from "@/lib/org-actions";
import { alertText } from "@/lib/org-form-state";
import { Alert } from "./ui/alert";
import { Select } from "./ui/select";

type Props = {
  action: (
    previous: OrgActionState,
    formData: FormData,
  ) => Promise<OrgActionState>;
  members: MemberAvailability[];
  /** The current assignee's membership id, or null. */
  currentId: string | null;
  /** Base for the element ids; two instances on one page (pane and header) need different ones. */
  idPrefix?: string;
};

/**
 * Admin only: picking a member (or nobody) saves at once, so there is no button to find. A quiet status line
 * says "Saving…" then "Saved"; a failure shows an alert and the select falls back to the saved assignee.
 */
export default function AssignConversationForm({
  action,
  members,
  currentId,
  idPrefix = "assignee",
}: Props) {
  const [state, formAction, pending] = useActionState(action, {});
  const saved = currentId ?? UNASSIGNED;
  const alert = alertText(state);
  const error = state.errors?.membershipId;
  const status = pending ? "Saving…" : (state.notice ?? "");

  return (
    <form action={formAction} className="flex flex-col gap-2">
      <label htmlFor={idPrefix} className="sr-only">
        Assignee
      </label>
      {/* Uncontrolled: React resets the form after the action, and a controlled select would fall back to its
          first option. The key remounts it with the new saved assignee as its default. */}
      <Select
        key={saved}
        id={idPrefix}
        name="membershipId"
        defaultValue={saved}
        onChange={(event) => event.currentTarget.form?.requestSubmit()}
        aria-busy={pending}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${idPrefix}-error` : `${idPrefix}-status`}
      >
        <option value={UNASSIGNED}>Unassigned</option>
        {members.map((member) => (
          <option key={member.membershipId} value={member.membershipId}>
            {memberOptionLabel(member)}
          </option>
        ))}
      </Select>
      {error && (
        <p id={`${idPrefix}-error`} className="text-sm text-danger">
          {error}
        </p>
      )}
      {alert && <Alert tone="danger">{alert}</Alert>}
      <p
        id={`${idPrefix}-status`}
        role="status"
        className="min-h-4 text-caption text-secondary"
      >
        {status}
      </p>
    </form>
  );
}
