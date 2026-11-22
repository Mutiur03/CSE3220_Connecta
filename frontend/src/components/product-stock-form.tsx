"use client";

import { useActionState, useRef } from "react";
import type { OrgActionState } from "@/lib/org-actions";
import { alertText } from "@/lib/org-form-state";
import { STOCK_REASON_MAX_LENGTH, STOCK_MOVEMENT_LABELS, STOCK_MOVEMENT_TYPES } from "@/lib/stock-validation";
import { Alert } from "./ui/alert";
import { Button, PendingLabel } from "./ui/button";
import { Field } from "./ui/field";
import { Select } from "./ui/select";
import { useFocusInvalidField } from "./use-focus-invalid-field";

type Props = {
  /** The server action bound to this organization and product. */
  action: (previous: OrgActionState, formData: FormData) => Promise<OrgActionState>;
};

/** Records a receive, issue or adjustment. Every member may use it, so it has no role gate. */
export default function ProductStockForm({ action }: Props) {
  const [state, formAction, pending] = useActionState(action, {});
  const formRef = useRef<HTMLFormElement>(null);
  useFocusInvalidField(formRef, state.errors);
  const alert = alertText(state);
  const value = (key: "type" | "quantity" | "reason", fallback: string) =>
    state.values?.[key] ?? fallback;

  return (
    <form ref={formRef} action={formAction} noValidate className="flex max-w-2xl flex-col gap-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="flex flex-col gap-1">
          <label htmlFor="stock-type" className="text-sm font-medium text-primary">
            Movement
          </label>
          {/* After a form action React resets the form, which restores each select to its
              option's defaultSelected. React never updates that for a changed defaultValue, so
              a new key remounts the select with the type the server kept. */}
          <Select
            key={value("type", "RECEIVE")}
            id="stock-type"
            name="type"
            autoComplete="off"
            defaultValue={value("type", "RECEIVE")}
          >
            {STOCK_MOVEMENT_TYPES.map((type) => (
              <option key={type} value={type}>
                {STOCK_MOVEMENT_LABELS[type]}
              </option>
            ))}
          </Select>
        </div>
        <Field
          id="stock-quantity"
          name="quantity"
          label="Quantity"
          hint="For Adjust, enter the new stock level."
          inputMode="numeric"
          autoComplete="off"
          defaultValue={value("quantity", "")}
          error={state.errors?.quantity}
        />
      </div>
      <Field
        id="stock-reason"
        name="reason"
        label="Reason"
        hint="Optional. Shown in the history."
        maxLength={STOCK_REASON_MAX_LENGTH}
        autoComplete="off"
        defaultValue={value("reason", "")}
        error={state.errors?.reason}
      />
      <div className="flex flex-wrap items-center gap-2">
        <Button type="submit" variant="primary" disabled={pending}>
          <PendingLabel pending={pending} busy="Recording…" idle="Record movement" />
        </Button>
      </div>
      {state.notice && <Alert tone="success">{state.notice}</Alert>}
      {alert && <Alert tone="danger">{alert}</Alert>}
    </form>
  );
}
