import { useEffect, type RefObject } from "react";
import type { OrgActionState } from "@/lib/org-actions";
import { firstFieldError } from "@/lib/org-form-state";

/**
 * After a failed submit, moves focus to the first field the action marked invalid,
 * so keyboard and screen reader users land on the error (WCAG 3.3.1).
 */
export function useFocusInvalidField(
  formRef: RefObject<HTMLFormElement | null>,
  errors: OrgActionState["errors"],
) {
  useEffect(() => {
    if (firstFieldError(errors) === undefined) return;
    formRef.current
      ?.querySelector<HTMLElement>('[aria-invalid="true"]')
      ?.focus();
  }, [formRef, errors]);
}
