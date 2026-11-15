"use client";

import { useActionState } from "react";
import type { OrgActionState } from "@/lib/org-actions";
import { ROLE_LABELS, type OrgRole } from "@/lib/org-permissions";
import { fieldClass } from "./org-styles";

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

/** Saves on change: picking a role submits the form, no extra button. */
export default function OrgRoleSelect({
  action,
  currentRole,
  options,
  label,
}: Props) {
  const [state, formAction, pending] = useActionState(action, {});

  return (
    <form action={formAction} className="flex flex-col gap-1">
      <select
        name="role"
        defaultValue={currentRole}
        aria-label={label}
        disabled={pending}
        onChange={(event) => event.currentTarget.form?.requestSubmit()}
        className={fieldClass}
      >
        {options.map((role) => (
          <option key={role} value={role}>
            {ROLE_LABELS[role]}
          </option>
        ))}
      </select>
      {state.message && (
        <p role="alert" className="text-sm text-red-600">
          {state.message}
        </p>
      )}
    </form>
  );
}
