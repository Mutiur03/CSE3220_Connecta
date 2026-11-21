"use client";

import { useActionState, useRef } from "react";
import { createOrganization, type OrgActionState } from "@/lib/org-actions";
import { alertText } from "@/lib/org-form-state";
import { Alert } from "./ui/alert";
import { Button, PendingLabel } from "./ui/button";
import { Field } from "./ui/field";
import { useFocusInvalidField } from "./use-focus-invalid-field";

export default function OrgCreateForm() {
  const [state, action, pending] = useActionState<OrgActionState, FormData>(
    createOrganization,
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
      className="flex max-w-md flex-col gap-4"
    >
      <Field
        id="org-name"
        name="name"
        label="Organization name"
        maxLength={100}
        defaultValue={state.values?.name}
        hint="Its web address is made from the name."
        error={state.errors?.name}
      />
      {alert && <Alert tone="danger">{alert}</Alert>}
      <Button type="submit" variant="primary" block disabled={pending}>
        <PendingLabel
          pending={pending}
          busy={"Creating…"}
          idle={"Create organization"}
        />
      </Button>
    </form>
  );
}
