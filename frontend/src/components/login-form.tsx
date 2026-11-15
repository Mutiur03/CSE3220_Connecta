"use client";

import { useActionState } from "react";
import { login, type LoginState } from "@/lib/auth-actions";
import { Button } from "@/components/ui/button";
import { fieldClasses } from "@/components/ui/classes";

const field = fieldClasses;

export default function LoginForm({ next = "" }: { next?: string }) {
  const [state, action, pending] = useActionState<LoginState, FormData>(
    login,
    {},
  );

  return (
    <form action={action} noValidate className="flex w-full flex-col gap-4">
      {/* Validated by the login action (safeRedirectPath) before any redirect. */}
      <input type="hidden" name="next" value={next} />
      <div className="flex flex-col gap-1">
        <label htmlFor="email" className="text-sm font-medium text-ink">
          Email
        </label>
        <input
          id="email"
          name="email"
          type="email"
          autoComplete="email"
          defaultValue={state.email}
          aria-invalid={state.errors?.email ? true : undefined}
          aria-describedby={state.errors?.email ? "email-error" : undefined}
          className={field}
        />
        {state.errors?.email && (
          <p id="email-error" className="text-sm text-danger">
            {state.errors.email}
          </p>
        )}
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor="password" className="text-sm font-medium text-ink">
          Password
        </label>
        <input
          id="password"
          name="password"
          type="password"
          autoComplete="current-password"
          aria-invalid={state.errors?.password ? true : undefined}
          aria-describedby={
            state.errors?.password ? "password-error" : undefined
          }
          className={field}
        />
        {state.errors?.password && (
          <p id="password-error" className="text-sm text-danger">
            {state.errors.password}
          </p>
        )}
      </div>

      <p role="alert" className="min-h-5 text-sm text-danger">
        {state.message}
      </p>

      <Button type="submit" variant="primary" disabled={pending}>
        {pending ? "Signing in…" : "Sign in"}
      </Button>
    </form>
  );
}
