import { request } from "./api";
import type { Message } from "./inbox-api";
import type { Page } from "./org-api";
import type { StockStatus } from "./product-validation";

/** One search hit. `imageUrl` is the same-origin proxy path of the product's first image, or null. */
export type ShareCandidate = {
  id: string;
  name: string;
  sku: string | null;
  unitPrice: number;
  stockStatus: StockStatus;
  imageUrl: string | null;
};

export type ShareSearchResult = Page<ShareCandidate>;

/** The composed message for one product, ready to edit. */
export type ShareProductPreview = {
  productId: string;
  text: string;
  imageUrl: string | null;
};

/** The image message (when sent) comes first, then the text. */
export type ProductShareResult = { messages: Message[] };

/** Search results shown at once; one page, the agent narrows with the query. */
export const SHARE_SEARCH_SIZE = 20;

const sharePath = (orgId: string, conversationId: string) =>
  `/api/organizations/${encodeURIComponent(orgId)}/conversations/${encodeURIComponent(conversationId)}`;

export const searchShareProducts = (
  accessToken: string,
  orgId: string,
  conversationId: string,
  query: string,
) => {
  const params = new URLSearchParams({
    page: "0",
    size: String(SHARE_SEARCH_SIZE),
  });
  if (query) params.set("q", query);
  return request<ShareSearchResult>(
    `${sharePath(orgId, conversationId)}/product-search?${params}`,
    { accessToken },
  );
};

export const previewProductShare = (
  accessToken: string,
  orgId: string,
  conversationId: string,
  productId: string,
) =>
  request<ShareProductPreview>(
    `${sharePath(orgId, conversationId)}/product-shares/preview?${new URLSearchParams({ productId })}`,
    { accessToken },
  );

export const sendProductShare = (
  accessToken: string,
  orgId: string,
  conversationId: string,
  productId: string,
  text: string,
) =>
  request<ProductShareResult>(`${sharePath(orgId, conversationId)}/product-shares`, {
    method: "POST",
    body: { productId, text },
    accessToken,
  });
