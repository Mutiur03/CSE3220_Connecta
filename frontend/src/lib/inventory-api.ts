import { request } from "./api";
import type { Page } from "./org-api";

/** Totals across the organization's products. stockValue is stock times unit price, 2 places. */
export type InventorySummary = {
  productCount: number;
  totalUnits: number;
  stockValue: number;
  lowStockCount: number;
  outOfStockCount: number;
};

export type StockMovementType = "RECEIVE" | "ISSUE" | "ADJUST";

export type StockMovement = {
  id: string;
  productId: string;
  productName: string;
  type: StockMovementType;
  quantityDelta: number;
  quantityAfter: number;
  reason: string | null;
  /** Null when the user who recorded the movement no longer exists. */
  createdBy: { id: string; fullName: string } | null;
  createdAt: string;
};

const inventoryPath = (orgId: string) =>
  `/api/organizations/${encodeURIComponent(orgId)}`;

export const getInventorySummary = (accessToken: string, orgId: string) =>
  request<InventorySummary>(`${inventoryPath(orgId)}/inventory/summary`, {
    accessToken,
  });

/** The organization's movements, newest first, across all products. */
export const listStockMovements = (
  accessToken: string,
  orgId: string,
  page: number,
  size: number,
) =>
  request<Page<StockMovement>>(
    `${inventoryPath(orgId)}/stock-movements?page=${page}&size=${size}`,
    { accessToken },
  );
