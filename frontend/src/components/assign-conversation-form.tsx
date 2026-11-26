"use client";

import { useActionState, useState } from "react";
import type { MemberAvailability } from "@/lib/assignment-api";
import { memberOptionLabel, UNASSIGNED } from "@/lib/assignment-view";
import type { OrgActionState } from "@/lib/org-actions";
import { alertText } from "@/lib/org-form-state";
import { Alert } from "./ui/alert";
import { Button, PendingLabel } from "./ui/button";
import { Select } from "./ui/select";

type Props = {
  action: (
    previous: OrgActionState,
    formData: FormData,
  ) => Promise<OrgActionState>;
  members: MemberAvailability[];
  /** The current assignee's membership id, or null. */
  currentId: string | null;
};

/** Admin only: pick a member (or nobody) and save. The button stays off until the pick differs from the saved assignee. */
export default function AssignConversationForm({
  action,
  members,
  currentId,
}: Props) {
  const [state, formAction, pending] = useActionState(action, {});
  const saved = currentId ?? UNASSIGNED;
  const [picked, setPicked] = useState(saved);
  const alert = alertText(state);
  const error = state.errors?.membershipId;

  return (
    <form action={formAction} className="flex flex-col gap-2">
      <div className="flex flex-wrap items-end gap-2">
        <div className="flex min-w-48 flex-1 flex-col gap-1">
          <label htmlFor="assignee" className="text-sm font-medium">
            Assignee
          </label>
          <Select
            id="assignee"
            name="membershipId"
            value={picked}
            onChange={(event) => setPicked(event.currentTarget.value)}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? "assignee-error" : undefined}
          >
            <option value={UNASSIGNED}>Unassigned</option>
            {members.map((member) => (
              <option key={member.membershipId} value={member.membershipId}>
                {memberOptionLabel(member)}
              </option>
            ))}
          </Select>
        </div>
        <Button
          type="submit"
          variant="primary"
          disabled={pending || picked === saved}
        >
          <PendingLabel pending={pending} busy="Saving…" idle="Save assignee" />
        </Button>
      </div>
      {error && (
        <p id="assignee-error" className="text-sm text-danger">
          {error}
        </p>
      )}
      {alert && <Alert tone="danger">{alert}</Alert>}
      {state.notice && <Alert tone="success">{state.notice}</Alert>}
    </form>
  );
}
