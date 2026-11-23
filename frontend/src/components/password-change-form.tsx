"use client";

import { useActionState, useRef } from "react";
import { changePassword } from "@/lib/password-actions";
import { alertText } from "@/lib/org-form-state";
import { PASSWORD_MIN } from "@/lib/validation";
import PasswordField from "./password-field";
import { Alert } from "./ui/alert";
import { Button, PendingLabel } from "./ui/button";
import { useFocusInvalidField } from "./use-focus-invalid-field";

/** `email` goes in a hidden username field, so password managers and screen readers know the account. */
export default function PasswordChangeForm({ email }: { email: string }) {
  const [state, formAction, pending] = useActionState(changePassword, {});
  const formRef = useRef<HTMLFormElement>(null);
  useFocusInvalidField(formRef, state.errors);
  const alert = alertText(state);

  // Passwords are never echoed back: React clears the fields after every submit.
  return (
    <form
      ref={formRef}
      action={formAction}
      noValidate
      className="flex max-w-md flex-col gap-3"
    >
      <input
        type="text"
        name="username"
        value={email}
        autoComplete="username"
        readOnly
        hidden
      />
      <PasswordField
        id="current-password"
        name="currentPassword"
        label="Current password"
        autoComplete="current-password"
        error={state.errors?.currentPassword}
      />
      <PasswordField
        id="new-password"
        name="newPassword"
        label="New password"
        autoComplete="new-password"
        hint={`At least ${PASSWORD_MIN} characters.`}
        error={state.errors?.newPassword}
      />
      <PasswordField
        id="confirm-password"
        name="confirmPassword"
        label="Confirm new password"
        autoComplete="new-password"
        error={state.errors?.confirmPassword}
      />
      <div>
        <Button type="submit" disabled={pending}>
          <PendingLabel
            pending={pending}
            busy="Changing…"
            idle="Change password"
          />
        </Button>
      </div>
      {alert && <Alert tone="danger">{alert}</Alert>}
      {state.notice && <Alert tone="success">{state.notice}</Alert>}
    </form>
  );
}
