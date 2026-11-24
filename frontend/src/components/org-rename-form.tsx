"use client";

import { useActionState, useRef } from "react";
import type { OrgActionState } from "@/lib/org-actions";
import { alertText } from "@/lib/org-form-state";
import { Alert } from "./ui/alert";
import { Button, PendingLabel } from "./ui/button";
import { Field } from "./ui/field";
import { useFocusInvalidField } from "./use-focus-invalid-field";

type Props = {
  action: (
    previous: OrgActionState,
    formData: FormData,
  ) => Promise<OrgActionState>;
  name: string;
};

export default function OrgRenameForm({ action, name }: Props) {
  const [state, formAction, pending] = useActionState(action, {});
  const formRef = useRef<HTMLFormElement>(null);
  useFocusInvalidField(formRef, state.errors);
  const alert = alertText(state);

  return (
    <form
      ref={formRef}
      action={formAction}
      noValidate
      className="flex max-w-xl flex-col gap-3"
    >
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start">
        <div className="min-w-0 flex-1">
          <Field
            id="rename-org"
            name="name"
            label="Organization name"
            maxLength={100}
            autoComplete="organization"
            defaultValue={state.values?.name ?? name}
            error={state.errors?.name}
          />
        </div>
        <Button type="submit" disabled={pending} className="self-start sm:mt-6">
          <PendingLabel pending={pending} busy={"Saving…"} idle={"Save name"} />
        </Button>
      </div>
      {alert && <Alert tone="danger">{alert}</Alert>}
      {state.notice && <Alert tone="success">{state.notice}</Alert>}
    </form>
  );
}
