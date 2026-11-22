import { request } from "./api";
import { listAllCategories } from "./category-api";
import type { Page } from "./org-api";
import {
  productListQuery,
  type ProductFilters,
  type StockStatus,
} from "./product-validation";

export type Product = {
  id: string;
  name: string;
  description: string | null;
  /** Null when the product has no category. */
  categoryId: string | null;
  categoryName: string | null;
  sku: string | null;
  unitPrice: number;
  /** Read only here; stock moves through recorded stock movements. */
  stockQuantity: number;
  reorderLevel: number;
  stockStatus: StockStatus;
  createdAt: string;
  updatedAt: string;
};

export type ProductInput = {
  name: string;
  description: string | null;
  categoryId: string | null;
  sku: string | null;
  unitPrice: number;
  reorderLevel: number;
};

/** A category as the product pickers show it. */
export type CategoryOption = { id: string; name: string };

const productsPath = (orgId: string) =>
  `/api/organizations/${encodeURIComponent(orgId)}/products`;

export const listProducts = (
  accessToken: string,
  orgId: string,
  filters: ProductFilters,
  page: number,
  size: number,
) =>
  request<Page<Product>>(
    `${productsPath(orgId)}?${productListQuery(filters, page, size)}`,
    { accessToken },
  );

/** Every category of the organization for a picker or filter, in the backend's order. */
export const listCategoryOptions = (accessToken: string, orgId: string) =>
  listAllCategories(accessToken, orgId).then((categories) =>
    categories.map(({ id, name }): CategoryOption => ({ id, name })),
  );

export const getProduct = (
  accessToken: string,
  orgId: string,
  productId: string,
) =>
  request<Product>(`${productsPath(orgId)}/${encodeURIComponent(productId)}`, {
    accessToken,
  });

export const createProduct = (
  accessToken: string,
  orgId: string,
  body: ProductInput,
) => request<Product>(productsPath(orgId), { method: "POST", body, accessToken });

export const updateProduct = (
  accessToken: string,
  orgId: string,
  productId: string,
  body: ProductInput,
) =>
  request<Product>(`${productsPath(orgId)}/${encodeURIComponent(productId)}`, {
    method: "PUT",
    body,
    accessToken,
  });

export const deleteProduct = (
  accessToken: string,
  orgId: string,
  productId: string,
) =>
  request<void>(`${productsPath(orgId)}/${encodeURIComponent(productId)}`, {
    method: "DELETE",
    accessToken,
  });
