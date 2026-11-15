"use client";

import { useActionState } from "react";
import { login, type LoginState } from "@/lib/auth-actions";

const field =
  "w-full rounded-md border border-zinc-300 bg-white px-3 py-2 text-zinc-900 outline-none focus:border-zinc-900 focus:ring-1 focus:ring-zinc-900 aria-invalid:border-red-600 dark:border-zinc-700 dark:bg-zinc-900 dark:text-zinc-100";

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
        <label htmlFor="email" className="text-sm font-medium">
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
          <p id="email-error" className="text-sm text-red-600">
            {state.errors.email}
          </p>
        )}
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor="password" className="text-sm font-medium">
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
          <p id="password-error" className="text-sm text-red-600">
            {state.errors.password}
          </p>
        )}
      </div>

      <p role="alert" className="min-h-5 text-sm text-red-600">
        {state.message}
      </p>

      <button
        type="submit"
        disabled={pending}
        className="rounded-md bg-zinc-900 px-4 py-2 font-medium text-white hover:bg-zinc-700 disabled:opacity-60 dark:bg-zinc-100 dark:text-zinc-900 dark:hover:bg-zinc-300"
      >
        {pending ? "Signing in…" : "Sign in"}
      </button>
    </form>
  );
}
