"use server";

import { revalidatePath } from "next/cache";
import { ApiError } from "./api";
import type { OrgActionState } from "./org-actions";
import { actionErrorMessage } from "./org-errors";
import * as imageApi from "./product-image-api";
import { imageUploadMessage, validateImageSelection } from "./product-image-validation";
import { getAccessToken } from "./session";

/** Uploads one image. Only an ADMIN may; the backend answers 403 otherwise and that shows as alert text. */
export async function uploadProductImage(
  orgId: string,
  productId: string,
  _previous: OrgActionState,
  formData: FormData,
): Promise<OrgActionState> {
  const file = formData.get("file");
  const error = validateImageSelection(file);
  if (error || !(file instanceof File)) return { errors: { file: error } };

  const productPath = `/organizations/${orgId}/products/${productId}`;
  const accessToken = await getAccessToken(productPath);
  try {
    await imageApi.uploadProductImage(accessToken, orgId, productId, file);
  } catch (caught) {
    const status = caught instanceof ApiError ? caught.status : undefined;
    return { message: imageUploadMessage(status, actionErrorMessage(caught)) };
  }
  revalidatePath(productPath);
  return { notice: "Image added." };
}

export async function deleteProductImage(
  orgId: string,
  productId: string,
  imageId: string,
): Promise<OrgActionState> {
  const productPath = `/organizations/${orgId}/products/${productId}`;
  const accessToken = await getAccessToken(productPath);
  try {
    await imageApi.deleteProductImage(accessToken, orgId, productId, imageId);
  } catch (error) {
    return { message: actionErrorMessage(error) };
  }
  revalidatePath(productPath);
  return {};
}
