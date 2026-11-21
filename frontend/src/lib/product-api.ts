import { request } from "./api";
import type { Page } from "./org-api";

export type Product = {
  id: string;
  name: string;
  description: string | null;
  /** Null when the product has no category. */
  categoryName: string | null;
  createdAt: string;
  updatedAt: string;
};

export type ProductInput = { name: string; description: string | null };

const productsPath = (orgId: string) =>
  `/api/organizations/${encodeURIComponent(orgId)}/products`;

export const listProducts = (
  accessToken: string,
  orgId: string,
  page: number,
  size: number,
) =>
  request<Page<Product>>(
    `${productsPath(orgId)}?page=${page}&size=${size}`,
    { accessToken },
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
