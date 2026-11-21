"use client";

import { useActionState, useId, useRef, useState } from "react";
import type { OrgActionState } from "@/lib/org-actions";
import { alertText } from "@/lib/org-form-state";
import { PRODUCT_IMAGE_TYPES, validateProductImage } from "@/lib/product-image-validation";
import { Alert } from "./ui/alert";
import { Button, PendingLabel } from "./ui/button";
import { fieldClasses } from "./ui/classes";
import { useFocusInvalidField } from "./use-focus-invalid-field";

type Props = {
  /** The server action bound to this organization and product. */
  action: (previous: OrgActionState, formData: FormData) => Promise<OrgActionState>;
};

const ACCEPT = PRODUCT_IMAGE_TYPES.join(",");

/**
 * Picks one image and uploads it. A file that is the wrong type or too large is refused on the
 * field right away, before any request. The server checks again and its errors show the same way.
 */
export default function ProductImageUploadForm({ action }: Props) {
  const [state, formAction, pending] = useActionState(action, {});
  const formRef = useRef<HTMLFormElement>(null);
  const inputId = useId();
  const [pickError, setPickError] = useState<string>();
  useFocusInvalidField(formRef, state.errors);
  const fieldError = pickError ?? state.errors?.file;
  const describedBy = fieldError ? `${inputId}-error` : `${inputId}-hint`;

  function checkPick(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.currentTarget.files?.[0];
    if (!file) return setPickError(undefined);
    const problem = validateProductImage(file);
    if (problem) event.currentTarget.value = "";
    setPickError(problem);
  }

  return (
    <form ref={formRef} action={formAction} noValidate className="flex max-w-xl flex-col gap-3">
      <div className="flex flex-col gap-1">
        <label htmlFor={inputId} className="text-sm font-medium text-primary">
          Image
        </label>
        <input
          id={inputId}
          name="file"
          type="file"
          accept={ACCEPT}
          onChange={checkPick}
          aria-invalid={fieldError ? true : undefined}
          aria-describedby={describedBy}
          className={`${fieldClasses} file:mr-3 file:border-0 file:bg-transparent file:p-0 file:font-medium file:text-primary`}
        />
        <p id={`${inputId}-hint`} className="text-caption text-secondary">
          JPEG, PNG or WebP, up to 5 MB.
        </p>
        {fieldError && (
          <p id={`${inputId}-error`} className="text-sm text-danger">
            {fieldError}
          </p>
        )}
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <Button type="submit" variant="primary" disabled={pending}>
          <PendingLabel pending={pending} busy="Uploading…" idle="Upload image" />
        </Button>
      </div>
      {state.notice && <Alert tone="success">{state.notice}</Alert>}
      {alertText(state) && <Alert tone="danger">{alertText(state)}</Alert>}
    </form>
  );
}
