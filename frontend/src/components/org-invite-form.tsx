"use client";

import { useActionState } from "react";
import type { OrgActionState } from "@/lib/org-actions";
import { ROLE_LABELS, type OrgRole } from "@/lib/org-permissions";
import { buttonClass, fieldClass } from "./org-styles";

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

  return (
    <div className="flex flex-col gap-4">
      <form
        action={formAction}
        noValidate
        className="flex max-w-xl flex-col gap-4 sm:flex-row sm:items-start"
      >
        <div className="flex flex-1 flex-col gap-1">
          <label htmlFor="invite-email" className="text-sm font-medium">
            Email
          </label>
          <input
            id="invite-email"
            name="email"
            type="email"
            maxLength={254}
            defaultValue={state.values?.email}
            aria-invalid={state.errors?.email ? true : undefined}
            aria-describedby={
              state.errors?.email ? "invite-email-error" : undefined
            }
            className={fieldClass}
          />
          {state.errors?.email && (
            <p id="invite-email-error" className="text-sm text-red-600">
              {state.errors.email}
            </p>
          )}
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="invite-role" className="text-sm font-medium">
            Role
          </label>
          <select
            id="invite-role"
            name="role"
            defaultValue={
              state.values?.role ?? roleOptions[roleOptions.length - 1]
            }
            className={fieldClass}
          >
            {roleOptions.map((role) => (
              <option key={role} value={role}>
                {ROLE_LABELS[role]}
              </option>
            ))}
          </select>
        </div>
        <button
          type="submit"
          disabled={pending}
          className={`${buttonClass("primary")} sm:mt-6`}
        >
          {pending ? "Creating…" : "Create invitation"}
        </button>
      </form>

      <p role="alert" className="text-sm text-red-600">
        {state.message ?? state.errors?.role}
      </p>

      {state.token && (
        <div
          role="status"
          className="flex max-w-xl flex-col gap-2 rounded-md border border-zinc-300 p-4 dark:border-zinc-700"
        >
          <p className="text-sm font-medium">
            {state.notice} Copy this token now. It is shown only once.
          </p>
          <input
            readOnly
            aria-label="Invitation token"
            value={state.token}
            onFocus={(event) => event.currentTarget.select()}
            className={`${fieldClass} font-mono text-xs`}
          />
          <p className="text-xs text-zinc-500">
            No email is sent yet. Pass the token to the invitee, who opens
            /invitations/accept and pastes it.
          </p>
        </div>
      )}
    </div>
  );
}
