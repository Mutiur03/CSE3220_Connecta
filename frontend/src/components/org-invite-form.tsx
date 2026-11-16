"use client";

import { useActionState, useRef } from "react";
import type { OrgActionState } from "@/lib/org-actions";
import { alertText } from "@/lib/org-form-state";
import { ROLE_LABELS, type OrgRole } from "@/lib/org-permissions";
import { Alert } from "./ui/alert";
import { Button } from "./ui/button";
import { Field } from "./ui/field";
import OrgTokenBox from "./org-token-box";
import { Select } from "./ui/select";
import { useFocusInvalidField } from "./use-focus-invalid-field";

type Props = {
  action: (
    previous: OrgActionState,
    formData: FormData,
  ) => Promise<OrgActionState>;
  /** The roles the caller may grant. */
  roleOptions: OrgRole[];
};

export default function OrgInviteForm({ action, roleOptions }: Props) {
  const [state, formAction, pending] = useActionState(action, {});
  const formRef = useRef<HTMLFormElement>(null);
  useFocusInvalidField(formRef, state.errors);
  const alert = alertText(state);

  return (
    <div className="flex flex-col gap-4">
      <form
        ref={formRef}
        action={formAction}
        noValidate
        className="flex max-w-xl flex-col gap-4 sm:flex-row sm:items-start"
      >
        <div className="flex-1">
          <Field
            id="invite-email"
            name="email"
            label="Email"
            type="email"
            maxLength={254}
            defaultValue={state.values?.email}
            error={state.errors?.email}
          />
        </div>
        <div className="flex flex-col gap-1">
          <label
            htmlFor="invite-role"
            className="text-sm font-medium text-primary"
          >
            Role
          </label>
          <Select
            id="invite-role"
            name="role"
            defaultValue={
              state.values?.role ?? roleOptions[roleOptions.length - 1]
            }
            aria-invalid={state.errors?.role ? true : undefined}
          >
            {roleOptions.map((role) => (
              <option key={role} value={role}>
                {ROLE_LABELS[role]}
              </option>
            ))}
          </Select>
        </div>
        <Button
          type="submit"
          variant="primary"
          disabled={pending}
          className="sm:mt-6"
        >
          {pending ? "Creating…" : "Create invitation"}
        </Button>
      </form>

      {alert && <Alert tone="danger">{alert}</Alert>}

      {state.token && (
        <div className="flex max-w-xl flex-col gap-3">
          <Alert tone="success">{state.notice}</Alert>
          <OrgTokenBox key={state.token} token={state.token} />
        </div>
      )}
    </div>
  );
}
