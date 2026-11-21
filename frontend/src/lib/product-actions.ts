"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import * as productApi from "./product-api";
import { actionErrorMessage } from "./org-errors";
import type { OrgActionState } from "./org-actions";
import { getAccessToken } from "./session";
import {
  validateProductDescription,
  validateProductName,
} from "./product-validation";

const productsPath = (orgId: string) => `/organizations/${orgId}/products`;

const field = (formData: FormData, name: string) =>
  String(formData.get(name) ?? "").trim();

/** Runs one backend call; a failure becomes a message for the form instead of an exception. */
async function attempt<T>(
  call: () => Promise<T>,
): Promise<{ ok: true; value: T } | { ok: false; message: string }> {
  try {
    return { ok: true, value: await call() };
  } catch (error) {
    return { ok: false, message: actionErrorMessage(error) };
  }
}

/** Reads the form, or returns the field errors to show on the form. */
function readProductForm(formData: FormData) {
  const name = field(formData, "name");
  const description = field(formData, "description");
  const values = { name, description };
  const errors = {
    name: validateProductName(name),
    description: validateProductDescription(description),
  };
  const hasError = Object.values(errors).some((error) => error !== undefined);
  return hasError
    ? { ok: false as const, state: { errors, values } }
    : { ok: true as const, input: { name, description: description || null }, values };
}

export async function createProduct(
  orgId: string,
  _previous: OrgActionState,
  formData: FormData,
): Promise<OrgActionState> {
  const form = readProductForm(formData);
  if (!form.ok) return form.state;

  const accessToken = await getAccessToken(productsPath(orgId));
  const result = await attempt(() =>
    productApi.createProduct(accessToken, orgId, form.input),
  );
  if (!result.ok) return { message: result.message, values: form.values };
  revalidatePath(productsPath(orgId));
  redirect(productsPath(orgId));
}

export async function updateProduct(
  orgId: string,
  productId: string,
  _previous: OrgActionState,
  formData: FormData,
): Promise<OrgActionState> {
  const form = readProductForm(formData);
  if (!form.ok) return form.state;

  const accessToken = await getAccessToken(productsPath(orgId));
  const result = await attempt(() =>
    productApi.updateProduct(accessToken, orgId, productId, form.input),
  );
  if (!result.ok) return { message: result.message, values: form.values };
  revalidatePath(productsPath(orgId));
  redirect(productsPath(orgId));
}

export async function deleteProduct(
  orgId: string,
  productId: string,
): Promise<OrgActionState> {
  const accessToken = await getAccessToken(productsPath(orgId));
  const result = await attempt(() =>
    productApi.deleteProduct(accessToken, orgId, productId),
  );
  if (!result.ok) return { message: result.message };
  revalidatePath(productsPath(orgId));
  return {};
}
