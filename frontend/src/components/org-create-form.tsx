"use client";

import { useActionState } from "react";
import { createOrganization, type OrgActionState } from "@/lib/org-actions";
import { buttonClass, fieldClass } from "./org-styles";

export default function OrgCreateForm() {
  const [state, action, pending] = useActionState<OrgActionState, FormData>(
    createOrganization,
    {},
  );

  return (
    <form action={action} noValidate className="flex max-w-md flex-col gap-4">
      <div className="flex flex-col gap-1">
        <label htmlFor="org-name" className="text-sm font-medium">
          Name
        </label>
        <input
          id="org-name"
          name="name"
          maxLength={100}
          defaultValue={state.values?.name}
          aria-invalid={state.errors?.name ? true : undefined}
          aria-describedby={state.errors?.name ? "org-name-error" : undefined}
          className={fieldClass}
        />
        {state.errors?.name && (
          <p id="org-name-error" className="text-sm text-red-600">
            {state.errors.name}
          </p>
        )}
      </div>

      <div className="flex flex-col gap-1">
        <label htmlFor="org-slug" className="text-sm font-medium">
          Slug
        </label>
        <input
          id="org-slug"
          name="slug"
          maxLength={60}
          defaultValue={state.values?.slug}
          aria-invalid={state.errors?.slug ? true : undefined}
          aria-describedby={
            state.errors?.slug ? "org-slug-error" : "org-slug-hint"
          }
          className={fieldClass}
        />
        <p id="org-slug-hint" className="text-xs text-zinc-500">
          Lowercase letters, digits and hyphens. It cannot be changed later.
        </p>
        {state.errors?.slug && (
          <p id="org-slug-error" className="text-sm text-red-600">
            {state.errors.slug}
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
        {pending ? "Creating…" : "Create organization"}
      </button>
    </form>
  );
}
