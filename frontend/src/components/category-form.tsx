"use client";

import Link from "next/link";
import { useActionState, useId, useRef } from "react";
import type { OrgActionState } from "@/lib/org-actions";
import {
  CATEGORY_DESCRIPTION_MAX_LENGTH,
  CATEGORY_NAME_MAX_LENGTH,
} from "@/lib/category-validation";
import { alertText } from "@/lib/org-form-state";
import { buttonClasses, fieldClasses } from "./ui/classes";
import { Alert } from "./ui/alert";
import { Button, PendingLabel } from "./ui/button";
import { Field } from "./ui/field";
import { useFocusInvalidField } from "./use-focus-invalid-field";

type Props = {
  action: (
    previous: OrgActionState,
    formData: FormData,
  ) => Promise<OrgActionState>;
  defaultName: string;
  defaultDescription: string;
  submitLabel: string;
  busyLabel: string;
  cancelHref: string;
};

/** The create and edit form for a category. Field errors show on their field; the alert is the backend's. */
export default function CategoryForm({
  action,
  defaultName,
  defaultDescription,
  submitLabel,
  busyLabel,
  cancelHref,
}: Props) {
  const [state, formAction, pending] = useActionState(action, {});
  const formRef = useRef<HTMLFormElement>(null);
  const descriptionId = useId();
  const descriptionError = state.errors?.description;
  useFocusInvalidField(formRef, state.errors);
  const alert = alertText(state);

  return (
    <form
      ref={formRef}
      action={formAction}
      noValidate
      className="flex max-w-xl flex-col gap-4"
    >
      <Field
        id="category-name"
        name="name"
        label="Name"
        maxLength={CATEGORY_NAME_MAX_LENGTH}
        autoComplete="off"
        defaultValue={state.values?.name ?? defaultName}
        error={state.errors?.name}
      />
      <div className="flex flex-col gap-1">
        <label htmlFor={descriptionId} className="text-sm font-medium text-primary">
          Description
        </label>
        <textarea
          id={descriptionId}
          name="description"
          rows={4}
          maxLength={CATEGORY_DESCRIPTION_MAX_LENGTH}
          autoComplete="off"
          defaultValue={state.values?.description ?? defaultDescription}
          aria-invalid={descriptionError ? true : undefined}
          aria-describedby={descriptionError ? `${descriptionId}-error` : undefined}
          className={`${fieldClasses} resize-y`}
        />
        {descriptionError && (
          <p id={`${descriptionId}-error`} className="text-sm text-danger">
            {descriptionError}
          </p>
        )}
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <Button type="submit" variant="primary" disabled={pending}>
          <PendingLabel pending={pending} busy={busyLabel} idle={submitLabel} />
        </Button>
        <Link href={cancelHref} className={buttonClasses("secondary")}>
          Cancel
        </Link>
      </div>
      {alert && <Alert tone="danger">{alert}</Alert>}
    </form>
  );
}
