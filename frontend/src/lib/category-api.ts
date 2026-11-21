import { request } from "./api";
import type { Page } from "./org-api";

export type Category = {
  id: string;
  name: string;
  description: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CategoryInput = { name: string; description: string | null };

const categoriesPath = (orgId: string) =>
  `/api/organizations/${encodeURIComponent(orgId)}/product-categories`;

const categoryPath = (orgId: string, categoryId: string) =>
  `${categoriesPath(orgId)}/${encodeURIComponent(categoryId)}`;

export const listCategories = (
  accessToken: string,
  orgId: string,
  page: number,
  size: number,
) =>
  request<Page<Category>>(`${categoriesPath(orgId)}?page=${page}&size=${size}`, {
    accessToken,
  });

export const getCategory = (
  accessToken: string,
  orgId: string,
  categoryId: string,
) => request<Category>(categoryPath(orgId, categoryId), { accessToken });

export const createCategory = (
  accessToken: string,
  orgId: string,
  body: CategoryInput,
) =>
  request<Category>(categoriesPath(orgId), { method: "POST", body, accessToken });

export const updateCategory = (
  accessToken: string,
  orgId: string,
  categoryId: string,
  body: CategoryInput,
) =>
  request<Category>(categoryPath(orgId, categoryId), {
    method: "PUT",
    body,
    accessToken,
  });

export const deleteCategory = (
  accessToken: string,
  orgId: string,
  categoryId: string,
) =>
  request<void>(categoryPath(orgId, categoryId), {
    method: "DELETE",
    accessToken,
  });
