"use client";

import Link from "next/link";
import { useActionState, useId, useRef } from "react";
import type { OrgActionState } from "@/lib/org-actions";
import {
  CATEGORY_DESCRIPTION_MAX_LENGTH,
  CATEGORY_NAME_MAX_LENGTH,
} from "@/lib/category-validation";
import { alertText } from "@/lib/org-form-state";
import { buttonClasses } from "./ui/classes";
import { FormFooter, FormPanel, FormSection } from "./form-layout";
import { Alert } from "./ui/alert";
import { Button, PendingLabel } from "./ui/button";
import { Field, TextareaField } from "./ui/field";
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
  useFocusInvalidField(formRef, state.errors);
  const alert = alertText(state);

  return (
    <form ref={formRef} action={formAction} noValidate>
      <FormPanel>
        <FormSection
          title="Details"
          description="Teammates pick the category on a product, and filter the product list by it."
        >
          <Field
            id="category-name"
            name="name"
            label="Name"
            maxLength={CATEGORY_NAME_MAX_LENGTH}
            autoComplete="off"
            defaultValue={state.values?.name ?? defaultName}
            hint="Short and plain, such as Beverages or Gift sets. No two categories share a name."
            error={state.errors?.name}
          />
          <TextareaField
            id={descriptionId}
            name="description"
            label="Description"
            labelNote="(optional)"
            rows={4}
            maxLength={CATEGORY_DESCRIPTION_MAX_LENGTH}
            autoComplete="off"
            defaultValue={state.values?.description ?? defaultDescription}
            hint="What belongs here, for the teammates who sort products into it."
            error={state.errors?.description}
          />
        </FormSection>
        <FormFooter alert={alert && <Alert tone="danger">{alert}</Alert>}>
          <Link href={cancelHref} className={buttonClasses("secondary")}>
            Cancel
          </Link>
          <Button type="submit" variant="primary" disabled={pending}>
            <PendingLabel
              pending={pending}
              busy={busyLabel}
              idle={submitLabel}
            />
          </Button>
        </FormFooter>
      </FormPanel>
    </form>
  );
}
