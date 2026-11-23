"use client";

import { useActionState, useState } from "react";
import type { OrgActionState } from "@/lib/org-actions";
import { isOrgRole, ROLE_LABELS, type OrgRole } from "@/lib/org-permissions";
import { Alert } from "./ui/alert";
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

/**
 * The role the select shows. While a save runs it shows the pick; otherwise the stored role, so a
 * save that fails puts the select back instead of leaving a role that was never saved.
 */
export function shownRole(
  picked: OrgRole,
  current: OrgRole,
  pending: boolean,
): OrgRole {
  return pending ? picked : current;
}

/** Picking a role saves it at once. The select is the only control: there is no Save button. */
export default function OrgRoleSelect({
  action,
  currentRole,
  options,
  label,
}: Props) {
  const [state, formAction, pending] = useActionState(action, {});
  const [picked, setPicked] = useState(currentRole);

  return (
    <form action={formAction} className="flex flex-col items-center gap-2">
      <div className="w-28">
        <Select
          controlSize="sm"
          name="role"
          value={shownRole(picked, currentRole, pending)}
          aria-label={label}
          autoComplete="off"
          disabled={pending}
          onChange={(event) => {
            const value = event.currentTarget.value;
            if (!isOrgRole(value) || value === currentRole) return;
            setPicked(value);
            event.currentTarget.form?.requestSubmit();
          }}
        >
          {options.map((role) => (
            <option key={role} value={role}>
              {ROLE_LABELS[role]}
            </option>
          ))}
        </Select>
      </div>
      {state.message && <Alert tone="danger">{state.message}</Alert>}
      {/* The select itself shows the new role; this tells a screen reader it was saved. */}
      <span role="status" className="sr-only">
        {state.notice}
      </span>
    </form>
  );
}
