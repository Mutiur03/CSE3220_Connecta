"use client";

import { useActionState } from "react";
import { alertText } from "@/lib/org-form-state";
import type { OrgActionState } from "@/lib/org-actions";
import { Alert } from "./ui/alert";

type Props = {
  /** Saves the switch; the page binds the organization and member. */
  action: (
    previous: OrgActionState,
    formData: FormData,
  ) => Promise<OrgActionState>;
  assignable: boolean;
  /** The member's name, for the switch's accessible name. */
  name: string;
};

/**
 * Admin only: flipping the switch saves at once. It is uncontrolled and keyed by the saved value, so after a
 * failed save React resets the form and it falls back to what is stored.
 */
export default function AssignableSwitch({ action, assignable, name }: Props) {
  const [state, formAction, pending] = useActionState(action, {});
  const alert = alertText(state);

  return (
    <form action={formAction} className="flex flex-col items-start gap-1">
      <label className="relative inline-flex min-h-10 min-w-10 items-center">
        <input
          key={String(assignable)}
          type="checkbox"
          role="switch"
          name="assignable"
          defaultChecked={assignable}
          aria-busy={pending}
          aria-label={`${name} can be assigned conversations automatically`}
          onChange={(event) => event.currentTarget.form?.requestSubmit()}
          className="peer sr-only"
        />
        <span
          aria-hidden="true"
          className="flex h-5 w-9 items-center rounded-full border border-line-strong bg-hover px-0.5 transition-colors duration-150 ease-out peer-checked:border-accent peer-checked:bg-accent peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-accent peer-checked:[&>span]:translate-x-4 peer-checked:[&>span]:bg-accent-ink"
        >
          <span className="size-3.5 rounded-full bg-secondary transition-transform duration-150 ease-out" />
        </span>
      </label>
      {alert && <Alert tone="danger">{alert}</Alert>}
      <span role="status" className="sr-only">
        {pending ? "Saving…" : (state.notice ?? "")}
      </span>
    </form>
  );
}
