"use client";

import Link from "next/link";
import { useActionState, useId, useRef } from "react";
import type { CategoryOption } from "@/lib/product-api";
import type { OrgActionState } from "@/lib/org-actions";
import { alertText } from "@/lib/org-form-state";
import { buttonClasses, fieldClasses } from "./ui/classes";
import { Alert } from "./ui/alert";
import { Button, PendingLabel } from "./ui/button";
import { Field } from "./ui/field";
import { Select } from "./ui/select";
import { useFocusInvalidField } from "./use-focus-invalid-field";
import {
  PRODUCT_DESCRIPTION_MAX_LENGTH,
  PRODUCT_NAME_MAX_LENGTH,
  PRODUCT_SKU_MAX_LENGTH,
} from "@/lib/product-validation";

/** Every field as text, the way the form sends and echoes them back. */
export type ProductFormValues = {
  name: string;
  description: string;
  sku: string;
  unitPrice: string;
  reorderLevel: string;
  categoryId: string;
};

type Props = {
  action: (
    previous: OrgActionState,
    formData: FormData,
  ) => Promise<OrgActionState>;
  defaults: ProductFormValues;
  categories: CategoryOption[];
  /** Shown read only on the edit page. Stock moves through recorded movements, not this form. */
  stockQuantity?: number;
  submitLabel: string;
  busyLabel: string;
  cancelHref: string;
};

/** The create and edit form for a product. Field errors show on their field; the alert is the backend's. */
export default function ProductForm({
  action,
  defaults,
  categories,
  stockQuantity,
  submitLabel,
  busyLabel,
  cancelHref,
}: Props) {
  const [state, formAction, pending] = useActionState(action, {});
  const formRef = useRef<HTMLFormElement>(null);
  const descriptionId = useId();
  const categoryId = useId();
  const descriptionError = state.errors?.description;
  useFocusInvalidField(formRef, state.errors);
  const alert = alertText(state);
  const value = (key: keyof ProductFormValues) =>
    state.values?.[key] ?? defaults[key];

  return (
    <form
      ref={formRef}
      action={formAction}
      noValidate
      className="flex max-w-xl flex-col gap-4"
    >
      <Field
        id="product-name"
        name="name"
        label="Name"
        maxLength={PRODUCT_NAME_MAX_LENGTH}
        autoComplete="off"
        defaultValue={value("name")}
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
          maxLength={PRODUCT_DESCRIPTION_MAX_LENGTH}
          autoComplete="off"
          defaultValue={value("description")}
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
      <div className="grid gap-4 sm:grid-cols-2">
        <Field
          id="product-sku"
          name="sku"
          label="SKU"
          hint="Optional. Unique in this organization."
          maxLength={PRODUCT_SKU_MAX_LENGTH}
          autoComplete="off"
          defaultValue={value("sku")}
          error={state.errors?.sku}
        />
        <div className="flex flex-col gap-1">
          <label htmlFor={categoryId} className="text-sm font-medium text-primary">
            Category
          </label>
          <Select
            id={categoryId}
            name="categoryId"
            autoComplete="off"
            defaultValue={value("categoryId")}
          >
            <option value="">No category</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </Select>
        </div>
        <Field
          id="product-price"
          name="unitPrice"
          label="Unit price"
          hint="Up to two decimal places. Blank means 0.00."
          inputMode="decimal"
          autoComplete="off"
          defaultValue={value("unitPrice")}
          error={state.errors?.unitPrice}
        />
        <Field
          id="product-reorder"
          name="reorderLevel"
          label="Reorder level"
          hint="Low-stock starts at this quantity. 0 turns it off."
          inputMode="numeric"
          autoComplete="off"
          defaultValue={value("reorderLevel")}
          error={state.errors?.reorderLevel}
        />
      </div>
      {stockQuantity !== undefined && (
        <div className="flex flex-col gap-1">
          <p className="text-sm font-medium text-primary">Stock quantity</p>
          <p className="tabular-nums text-primary">{stockQuantity}</p>
          <p className="text-caption text-secondary">
            Stock changes are recorded on the product&apos;s detail page.
          </p>
        </div>
      )}
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
