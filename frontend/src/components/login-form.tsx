"use client";

import { useActionState, useEffect, useRef } from "react";
import { login, type LoginState } from "@/lib/auth-actions";
import { Button, PendingLabel } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import PasswordField from "@/components/password-field";

export default function LoginForm({ next = "" }: { next?: string }) {
  const formRef = useRef<HTMLFormElement>(null);
  const [state, action, pending] = useActionState<LoginState, FormData>(
    login,
    {},
  );

  // A failed submit moves focus to the field to fix, so the error is announced. A wrong password
  // is not a field error; the form clears the password, so focus goes there.
  useEffect(() => {
    if (!state.errors && !state.message) return;
    const name = state.errors?.email ? "email" : "password";
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
      {/* Validated by the login action (safeRedirectPath) before any redirect. */}
      <input type="hidden" name="next" value={next} />
      <Field
        id="email"
        name="email"
        type="email"
        label="Email"
        placeholder="you@company.com"
        autoComplete="email"
        defaultValue={state.email}
        error={state.errors?.email}
      />
      <PasswordField
        id="password"
        name="password"
        label="Password"
        placeholder="Enter your password"
        autoComplete="current-password"
        error={state.errors?.password}
      />

      {state.message && (
        <p role="alert" className="text-sm text-danger">
          {state.message}
        </p>
      )}

      <Button type="submit" variant="primary" block disabled={pending}>
        <PendingLabel pending={pending} busy={"Signing in…"} idle={"Sign in"} />
      </Button>
    </form>
  );
}
