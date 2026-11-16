"use client";

import { useActionState, useState } from "react";
import type { OrgActionState } from "@/lib/org-actions";
import { isOrgRole, ROLE_LABELS, type OrgRole } from "@/lib/org-permissions";
import { Alert } from "./ui/alert";
import { Button } from "./ui/button";
import { Select } from "./ui/select";

type Props = {
  action: (
    previous: OrgActionState,
    formData: FormData,
  ) => Promise<OrgActionState>;
  currentRole: OrgRole;
  /** The roles the caller may grant. */
  options: OrgRole[];
  label: string;
};

/** A save is offered only for a real change, and never while a save is running. */
export function canSaveRole(
  picked: OrgRole,
  current: OrgRole,
  pending: boolean,
): boolean {
  return !pending && picked !== current;
}

// "Role for Ana" -> "Save role for Ana", so each button names its row.
function saveLabel(label: string): string {
  return `Save ${label.charAt(0).toLowerCase()}${label.slice(1)}`;
}

/** Picking a role changes the select only. Nothing is sent until Save is pressed. */
export default function OrgRoleSelect({
  action,
  currentRole,
  options,
  label,
}: Props) {
  const [state, formAction, pending] = useActionState(action, {});
  const [picked, setPicked] = useState(currentRole);
  const [seenRole, setSeenRole] = useState(currentRole);

  // The stored role changes when the page revalidates after a save. Follow it.
  if (seenRole !== currentRole) {
    setSeenRole(currentRole);
    setPicked(currentRole);
  }

  const showNotice = Boolean(state.notice) && picked === currentRole;

  return (
    <div className="flex flex-col gap-2">
      <form action={formAction} className="flex flex-wrap items-center gap-2">
        <div className="w-40 max-w-full">
          <Select
            name="role"
            value={picked}
            aria-label={label}
            disabled={pending}
            onChange={(event) => {
              const value = event.currentTarget.value;
              if (isOrgRole(value)) setPicked(value);
            }}
          >
            {options.map((role) => (
              <option key={role} value={role}>
                {ROLE_LABELS[role]}
              </option>
            ))}
          </Select>
        </div>
        <Button
          type="submit"
          disabled={!canSaveRole(picked, currentRole, pending)}
          aria-label={saveLabel(label)}
        >
          {pending ? "Saving…" : "Save"}
        </Button>
      </form>
      {state.message && <Alert tone="danger">{state.message}</Alert>}
      {showNotice && <Alert tone="success">{state.notice}</Alert>}
    </div>
  );
}
