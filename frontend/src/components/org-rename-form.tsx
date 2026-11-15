"use client";

import { useActionState } from "react";
import type { OrgActionState } from "@/lib/org-actions";
import { buttonClass, fieldClass } from "./org-styles";

type Props = {
  action: (
    previous: OrgActionState,
    formData: FormData,
  ) => Promise<OrgActionState>;
  name: string;
};

export default function OrgRenameForm({ action, name }: Props) {
  const [state, formAction, pending] = useActionState(action, {});

  return (
    <form
      action={formAction}
      noValidate
      className="flex max-w-md flex-col gap-3"
    >
      <div className="flex flex-col gap-1">
        <label htmlFor="rename-org" className="text-sm font-medium">
          Organization name
        </label>
        <input
          id="rename-org"
          name="name"
          maxLength={100}
          defaultValue={state.values?.name ?? name}
          aria-invalid={state.errors?.name ? true : undefined}
          aria-describedby={state.errors?.name ? "rename-org-error" : undefined}
          className={fieldClass}
        />
        {state.errors?.name && (
          <p id="rename-org-error" className="text-sm text-red-600">
            {state.errors.name}
          </p>
        )}
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <button type="submit" disabled={pending} className={buttonClass()}>
          {pending ? "Saving…" : "Save name"}
        </button>
        <p role="alert" className="text-sm text-red-600">
          {state.message}
        </p>
        {state.notice && (
          <p role="status" className="text-sm text-zinc-500">
            {state.notice}
          </p>
        )}
      </div>
    </form>
  );
}
