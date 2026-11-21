"use client";

import { useActionState, useEffect, useRef, useState } from "react";
import {
  acceptInvitationAsNewUser,
  type NewUserInvitationState,
} from "@/lib/auth-actions";
import { FULL_NAME_MAX, PASSWORD_MAX, PASSWORD_MIN } from "@/lib/validation";
import PasswordField from "./password-field";
import { Button, PendingLabel } from "./ui/button";
import { Field } from "./ui/field";

/** An invited person with no account yet: the email is fixed by the invitation, they add a name and password. */
export default function AcceptNewForm({
  token,
  email,
}: {
  token: string;
  email: string;
}) {
  const formRef = useRef<HTMLFormElement>(null);
  // Held in state so a failed submit does not wipe the password; it never leaves the browser
  // except in the submit itself.
  const [password, setPassword] = useState("");
  const [state, action, pending] = useActionState<
    NewUserInvitationState,
    FormData
  >(acceptInvitationAsNewUser, {});

  useEffect(() => {
    if (!state.errors) return;
    const name = state.errors.fullName ? "fullName" : "password";
    const field = formRef.current?.elements.namedItem(name);
    if (field instanceof HTMLInputElement) field.focus();
  }, [state]);

  return (
    <form
      ref={formRef}
      action={action}
      noValidate
      aria-busy={pending || undefined}
      className="flex w-full flex-col gap-4"
    >
      <input type="hidden" name="token" value={token} />
      <Field
        id="invited-email"
        label="Email"
        value={email}
        readOnly
        autoComplete="email"
      />
      <Field
        id="fullName"
        name="fullName"
        label="Full name"
        autoComplete="name"
        maxLength={FULL_NAME_MAX}
        defaultValue={state.fullName}
        error={state.errors?.fullName}
      />
      <PasswordField
        id="password"
        name="password"
        label="Password"
        autoComplete="new-password"
        maxLength={PASSWORD_MAX}
        value={password}
        onChange={(event) => setPassword(event.target.value)}
        hint={`At least ${PASSWORD_MIN} characters`}
        error={state.errors?.password}
      />

      <p role="alert" className="min-h-5 text-sm text-danger">
        {state.message}
      </p>

      <Button type="submit" variant="primary" block disabled={pending}>
        <PendingLabel
          pending={pending}
          busy={"Joining…"}
          idle={"Create account and join"}
        />
      </Button>
    </form>
  );
}
