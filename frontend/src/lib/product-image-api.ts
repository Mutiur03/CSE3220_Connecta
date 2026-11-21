import { request } from "./api";

export type ProductImage = {
  id: string;
  contentType: string;
  sizeBytes: number;
  position: number;
  /** The authenticated backend path. The page never uses it: images load through the proxy route. */
  contentPath: string;
  createdAt: string;
};

const imagesPath = (orgId: string, productId: string) =>
  `/api/organizations/${encodeURIComponent(orgId)}/products/${encodeURIComponent(productId)}/images`;

/** The same-origin address an image is shown from. The proxy route adds the access token. */
export const productImageSrc = (orgId: string, productId: string, imageId: string) =>
  `/api/organizations/${encodeURIComponent(orgId)}/products/${encodeURIComponent(productId)}/images/${encodeURIComponent(imageId)}/content`;

export const listProductImages = (accessToken: string, orgId: string, productId: string) =>
  request<ProductImage[]>(imagesPath(orgId, productId), { accessToken });

export const uploadProductImage = (
  accessToken: string,
  orgId: string,
  productId: string,
  file: File,
) => {
  const form = new FormData();
  form.append("file", file);
  return request<ProductImage>(imagesPath(orgId, productId), {
    method: "POST",
    form,
    accessToken,
  });
};

export const deleteProductImage = (
  accessToken: string,
  orgId: string,
  productId: string,
  imageId: string,
) =>
  request<void>(`${imagesPath(orgId, productId)}/${encodeURIComponent(imageId)}`, {
    method: "DELETE",
    accessToken,
  });
