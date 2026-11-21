import { request } from "./api";
import type { Page } from "./org-api";
import type { StockMovementType } from "./stock-validation";

export type StockMovement = {
  id: string;
  productId: string;
  productName: string;
  type: StockMovementType;
  /** Signed change: positive for stock in, negative for stock out. */
  quantityDelta: number;
  /** Stock on hand after this movement. */
  quantityAfter: number;
  reason: string | null;
  /** Null when the user who recorded the movement no longer exists. */
  createdBy: { id: string; fullName: string } | null;
  createdAt: string;
};

export type StockMovementInput = {
  type: StockMovementType;
  quantity: number;
  reason: string | null;
};

const movementsPath = (orgId: string, productId: string) =>
  `/api/organizations/${encodeURIComponent(orgId)}/products/${encodeURIComponent(productId)}/stock-movements`;

/** One page of a product's movements, newest first. */
export const listStockMovements = (
  accessToken: string,
  orgId: string,
  productId: string,
  page: number,
  size: number,
) =>
  request<Page<StockMovement>>(`${movementsPath(orgId, productId)}?page=${page}&size=${size}`, {
    accessToken,
  });

export const recordStockMovement = (
  accessToken: string,
  orgId: string,
  productId: string,
  body: StockMovementInput,
) =>
  request<StockMovement>(movementsPath(orgId, productId), {
    method: "POST",
    body,
    accessToken,
  });
