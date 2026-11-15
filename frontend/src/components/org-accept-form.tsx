"use client";

import { useActionState, useRef } from "react";
import { acceptInvitation, type OrgActionState } from "@/lib/org-actions";
import { alertText } from "@/lib/org-form-state";
import { Alert } from "./ui/alert";
import { Button } from "./ui/button";
import { Field } from "./ui/field";
import { useFocusInvalidField } from "./use-focus-invalid-field";

export default function OrgAcceptForm({
  initialToken,
}: {
  initialToken: string;
}) {
  const [state, action, pending] = useActionState<OrgActionState, FormData>(
    acceptInvitation,
    {},
  );
  const formRef = useRef<HTMLFormElement>(null);
  useFocusInvalidField(formRef, state.errors);
  const alert = alertText(state);

  return (
    <form
      ref={formRef}
      action={action}
      noValidate
      className="flex w-full max-w-md flex-col gap-4"
    >
      <Field
        id="invite-token"
        name="token"
        label="Invitation token"
        autoComplete="off"
        defaultValue={state.values?.token ?? initialToken}
        error={state.errors?.token}
        className="font-mono text-xs"
      />
      {alert && <Alert tone="danger">{alert}</Alert>}
      <Button type="submit" variant="primary" disabled={pending}>
        {pending ? "Accepting…" : "Accept invitation"}
      </Button>
    </form>
  );
}
