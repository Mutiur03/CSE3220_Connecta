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
      className="flex max-w-md flex-col gap-3"
    >
      <Field
        id="rename-org"
        name="name"
        label="Organization name"
        maxLength={100}
        defaultValue={state.values?.name ?? name}
        error={state.errors?.name}
      />
      <div>
        <Button type="submit" disabled={pending}>
          <PendingLabel pending={pending} busy={"Saving…"} idle={"Save name"} />
        </Button>
      </div>
      {alert && <Alert tone="danger">{alert}</Alert>}
      {state.notice && <Alert tone="success">{state.notice}</Alert>}
    </form>
  );
}
