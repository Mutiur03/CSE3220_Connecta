"use client";

import { useActionState } from "react";
import { acceptInvitation, type OrgActionState } from "@/lib/org-actions";
import { buttonClass, fieldClass } from "./org-styles";

export default function OrgAcceptForm({
  initialToken,
}: {
  initialToken: string;
}) {
  const [state, action, pending] = useActionState<OrgActionState, FormData>(
    acceptInvitation,
    {},
  );

  return (
    <form
      action={action}
      noValidate
      className="flex w-full max-w-md flex-col gap-4"
    >
      <div className="flex flex-col gap-1">
        <label htmlFor="invite-token" className="text-sm font-medium">
          Invitation token
        </label>
        <input
          id="invite-token"
          name="token"
          autoComplete="off"
          defaultValue={state.values?.token ?? initialToken}
          aria-invalid={state.errors?.token ? true : undefined}
          aria-describedby={
            state.errors?.token ? "invite-token-error" : undefined
          }
          className={`${fieldClass} font-mono text-xs`}
        />
        {state.errors?.token && (
          <p id="invite-token-error" className="text-sm text-red-600">
            {state.errors.token}
          </p>
        )}
      </div>
      <p role="alert" className="min-h-5 text-sm text-red-600">
        {state.message}
      </p>
      <button
        type="submit"
        disabled={pending}
        className={buttonClass("primary")}
      >
        {pending ? "Accepting…" : "Accept invitation"}
      </button>
    </form>
  );
}
