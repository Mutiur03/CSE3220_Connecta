"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { ApiError } from "./api";
import * as categoryApi from "./category-api";
import {
  validateCategoryDescription,
  validateCategoryName,
} from "./category-validation";
import { actionErrorMessage } from "./org-errors";
import type { OrgActionState } from "./org-actions";
import { getAccessToken } from "./session";

const CONFLICT = 409;
const DUPLICATE_NAME = "A category with this name already exists.";

const categoriesPath = (orgId: string) => `/organizations/${orgId}/categories`;

const field = (formData: FormData, name: string) =>
  String(formData.get(name) ?? "").trim();

/** Reads the form, or returns the field errors to show on the form. */
function readCategoryForm(formData: FormData) {
  const name = field(formData, "name");
  const description = field(formData, "description");
  const values = { name, description };
  const errors = {
    name: validateCategoryName(name),
    description: validateCategoryDescription(description),
  };
  const hasError = Object.values(errors).some((error) => error !== undefined);
  return hasError
    ? { ok: false as const, state: { errors, values } }
    : { ok: true as const, input: { name, description: description || null }, values };
}

/** A failed save: a duplicate name belongs on the name field, anything else in the alert. */
function saveFailure(error: unknown, values: Record<string, string>): OrgActionState {
  if (error instanceof ApiError && error.status === CONFLICT)
    return { errors: { name: DUPLICATE_NAME }, values };
  return { message: actionErrorMessage(error), values };
}

export async function createCategory(
  orgId: string,
  _previous: OrgActionState,
  formData: FormData,
): Promise<OrgActionState> {
  const form = readCategoryForm(formData);
  if (!form.ok) return form.state;

  const accessToken = await getAccessToken(categoriesPath(orgId));
  try {
    await categoryApi.createCategory(accessToken, orgId, form.input);
  } catch (error) {
    return saveFailure(error, form.values);
  }
  revalidatePath(categoriesPath(orgId));
  redirect(categoriesPath(orgId));
}

export async function updateCategory(
  orgId: string,
  categoryId: string,
  _previous: OrgActionState,
  formData: FormData,
): Promise<OrgActionState> {
  const form = readCategoryForm(formData);
  if (!form.ok) return form.state;

  const accessToken = await getAccessToken(categoriesPath(orgId));
  try {
    await categoryApi.updateCategory(accessToken, orgId, categoryId, form.input);
  } catch (error) {
    return saveFailure(error, form.values);
  }
  revalidatePath(categoriesPath(orgId));
  redirect(categoriesPath(orgId));
}

export async function deleteCategory(
  orgId: string,
  categoryId: string,
): Promise<OrgActionState> {
  const accessToken = await getAccessToken(categoriesPath(orgId));
  try {
    await categoryApi.deleteCategory(accessToken, orgId, categoryId);
  } catch (error) {
    return { message: actionErrorMessage(error) };
  }
  revalidatePath(categoriesPath(orgId));
  return {};
}
