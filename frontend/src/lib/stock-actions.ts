"use server";

import { revalidatePath } from "next/cache";
import { ApiError } from "./api";
import { actionErrorMessage } from "./org-errors";
import type { OrgActionState } from "./org-actions";
import * as stockApi from "./stock-api";
import { getAccessToken } from "./session";
import {
  SHORT_STOCK_MESSAGE,
  isStockMovementType,
  validateStockQuantity,
  validateStockReason,
} from "./stock-validation";

const UNPROCESSABLE = 422;

const field = (formData: FormData, name: string) =>
  String(formData.get(name) ?? "").trim();

/**
 * Records a RECEIVE, ISSUE or ADJUST movement. Any member may do it. Field problems show on their
 * field; an ISSUE beyond the stock on hand comes back as a 422 and shows on the quantity field too.
 */
export async function recordStockMovement(
  orgId: string,
  productId: string,
  _previous: OrgActionState,
  formData: FormData,
): Promise<OrgActionState> {
  const type = field(formData, "type");
  const quantity = field(formData, "quantity");
  const reason = field(formData, "reason");
  const values = { type, quantity, reason };
  if (!isStockMovementType(type)) return { message: "Choose receive, issue or adjust.", values };

  const errors = {
    quantity: validateStockQuantity(type, quantity),
    reason: validateStockReason(reason),
  };
  if (errors.quantity || errors.reason) return { errors, values };

  const productPath = `/organizations/${orgId}/products/${productId}`;
  const accessToken = await getAccessToken(productPath);
  try {
    await stockApi.recordStockMovement(accessToken, orgId, productId, {
      type,
      quantity: Number(quantity),
      reason: reason || null,
    });
  } catch (error) {
    if (error instanceof ApiError && error.status === UNPROCESSABLE)
      return { errors: { quantity: SHORT_STOCK_MESSAGE }, values };
    return { message: actionErrorMessage(error), values };
  }
  revalidatePath(productPath);
  return { notice: "Movement recorded." };
}
