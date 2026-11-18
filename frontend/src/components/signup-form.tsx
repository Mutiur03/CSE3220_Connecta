"use client";

import { useActionState, useEffect, useRef, useState } from "react";
import type { SignupState } from "@/lib/auth-actions";
import {
  EMAIL_MAX,
  FULL_NAME_MAX,
  PASSWORD_MAX,
  PASSWORD_MIN,
} from "@/lib/validation";
import { Button, PendingLabel } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import PasswordField from "@/components/password-field";

const FIELD_ORDER = ["fullName", "email", "password"] as const;

type SignupFormProps = {
  /** signup, or setup for the system owner. */
  action: (previous: SignupState, formData: FormData) => Promise<SignupState>;
  submitLabel: string;
  pendingLabel: string;
  next?: string;
};

export default function SignupForm({
  action: submit,
  submitLabel,
  pendingLabel,
  next = "",
}: SignupFormProps) {
  const formRef = useRef<HTMLFormElement>(null);
  // Held in state so a failed submit (a taken email) does not wipe what was typed. It never
  // leaves the browser except in the submit itself.
  const [password, setPassword] = useState("");
  const [state, action, pending] = useActionState<SignupState, FormData>(
    submit,
    {},
  );

  // A failed submit moves focus to the first field to fix, so the error is announced.
  useEffect(() => {
    if (!state.errors) return;
    const name = FIELD_ORDER.find((field) => state.errors?.[field]);
    const field = name && formRef.current?.elements.namedItem(name);
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
      {/* Validated by the signup action (safeRedirectPath) before any redirect. */}
      <input type="hidden" name="next" value={next} />
      <Field
        id="fullName"
        name="fullName"
        label="Full name"
        placeholder="Ada Lovelace"
        autoComplete="name"
        maxLength={FULL_NAME_MAX}
        defaultValue={state.fullName}
        error={state.errors?.fullName}
      />
      <Field
        id="email"
        name="email"
        type="email"
        label="Work email"
        placeholder="you@company.com"
        autoComplete="email"
        maxLength={EMAIL_MAX}
        defaultValue={state.email}
        error={state.errors?.email}
      />
      <PasswordField
        id="password"
        name="password"
        label="Password"
        placeholder="Choose a password"
        autoComplete="new-password"
        value={password}
        onChange={(event) => setPassword(event.target.value)}
        maxLength={PASSWORD_MAX}
        hint={`At least ${PASSWORD_MIN} characters`}
        error={state.errors?.password}
      />

      {state.message && (
        <p role="alert" className="text-sm text-danger">
          {state.message}
        </p>
      )}

      <Button type="submit" variant="primary" block disabled={pending}>
        <PendingLabel
          pending={pending}
          busy={pendingLabel}
          idle={submitLabel}
        />
      </Button>
    </form>
  );
}
