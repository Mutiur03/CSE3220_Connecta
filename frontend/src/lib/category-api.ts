import { ApiError, request } from "./api";
import { MAX_PAGE_SIZE, type Page } from "./org-api";

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

/**
 * Every category of the organization, in the backend's order. The first page gives the
 * total, the remaining pages are fetched in parallel and joined in page order.
 */
export async function listAllCategories(
  accessToken: string,
  orgId: string,
): Promise<Category[]> {
  const first = await listCategories(accessToken, orgId, 0, MAX_PAGE_SIZE);
  const pageCount = Math.ceil(first.totalElements / MAX_PAGE_SIZE);
  const rest = await Promise.all(
    Array.from({ length: Math.max(pageCount - 1, 0) }, (_, i) =>
      listCategories(accessToken, orgId, i + 1, MAX_PAGE_SIZE),
    ),
  );
  const pages = [first, ...rest];
  // A page that should hold rows but is empty means the server changed under us or is stuck.
  // Fail instead of returning a silently short list.
  if (
    pages.some((page, index) => index < pageCount && page.content.length === 0)
  ) {
    throw new ApiError(502, "Category list ended before the reported total");
  }
  return pages.flatMap((page) => page.content);
}

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
