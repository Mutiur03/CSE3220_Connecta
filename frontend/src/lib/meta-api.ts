import { request } from "./api";
import type { Page } from "./org-api";

/** Whether the organization is connected to Meta (Facebook Login for Business). No token is ever sent. */
export type MetaConnection = {
  connected: boolean;
  connectedAt: string | null;
  expiresAt: string | null;
};

/** A Page the Meta grant includes, and whether this organization has connected it. */
export type MetaAvailablePage = {
  pageId: string;
  name: string;
  connected: boolean;
};

/** Why the last import of past conversations failed. Absent (null) when it did not. */
export type HistoryImportError = "PERMISSION" | "FETCH";

/** A Facebook Page this organization has connected for Messenger. */
export type MetaConnectedPage = {
  pageId: string;
  name: string;
  connectedAt: string;
  historyImportError: HistoryImportError | null;
  historyImportFailedAt: string | null;
};

/** An Instagram professional account this organization has connected. */
export type MetaInstagramAccount = {
  id: string;
  externalAccountId: string;
  username: string | null;
  connectedAt: string;
  historyImportError: HistoryImportError | null;
  historyImportFailedAt: string | null;
};

const metaPath = (orgId: string) =>
  `/api/organizations/${encodeURIComponent(orgId)}/meta`;

export const getMetaConnection = (accessToken: string, orgId: string) =>
  request<MetaConnection>(`${metaPath(orgId)}/authorization`, { accessToken });

/** Starts the Facebook login. The browser must be sent to the returned URL. */
export const startMetaConnection = (accessToken: string, orgId: string) =>
  request<{ authorizationUrl: string }>(`${metaPath(orgId)}/authorization`, {
    method: "POST",
    accessToken,
  });

export const listAvailablePages = (accessToken: string, orgId: string) =>
  request<MetaAvailablePage[]>(`${metaPath(orgId)}/pages/available`, {
    accessToken,
  });

export const listConnectedPages = (
  accessToken: string,
  orgId: string,
  page: number,
  size: number,
) =>
  request<Page<MetaConnectedPage>>(
    `${metaPath(orgId)}/pages?page=${page}&size=${size}`,
    { accessToken },
  );

/** Connects the chosen Pages. Every id must be one the grant includes; otherwise the request fails. */
export const connectPages = (
  accessToken: string,
  orgId: string,
  pageIds: string[],
) =>
  request<MetaConnectedPage[]>(`${metaPath(orgId)}/pages`, {
    method: "POST",
    body: { pageIds },
    accessToken,
  });

/** Starts Instagram Business Login. The browser must be sent to the returned URL. */
export const startInstagramConnection = (accessToken: string, orgId: string) =>
  request<{ authorizationUrl: string }>(`${metaPath(orgId)}/instagram`, {
    method: "POST",
    accessToken,
  });

export const listInstagramAccounts = (
  accessToken: string,
  orgId: string,
  page: number,
  size: number,
) =>
  request<Page<MetaInstagramAccount>>(
    `${metaPath(orgId)}/instagram?page=${page}&size=${size}`,
    { accessToken },
  );
