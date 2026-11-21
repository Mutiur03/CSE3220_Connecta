// Backend REST API base URL. Server code prefers API_URL (runtime, server-only, e.g. http://app:8080 in
// Docker); the browser has no API_URL, so it falls back to NEXT_PUBLIC_API_URL (the public address).
// Read per call, not at module load, so standalone images pick up runtime env.
export function apiBaseUrl(): string {
  return (
    process.env.API_URL ??
    process.env.NEXT_PUBLIC_API_URL ??
    "http://localhost:8080"
  );
}

export type User = {
  id: string;
  email: string;
  fullName: string;
  roles: string[];
};

export type Tokens = {
  tokenType: string;
  accessToken: string;
  /** Access token lifetime in seconds. */
  expiresIn: number;
  refreshToken: string;
  user: User;
};

/** A non-2xx answer from the backend (RFC 9457 problem details). */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

export async function request<T>(
  path: string,
  init: { method?: string; body?: unknown; accessToken?: string } = {},
): Promise<T> {
  const res = await fetch(`${apiBaseUrl()}${path}`, {
    method: init.method ?? "GET",
    headers: {
      ...(init.body === undefined
        ? {}
        : { "Content-Type": "application/json" }),
      ...(init.accessToken
        ? { Authorization: `Bearer ${init.accessToken}` }
        : {}),
    },
    body: init.body === undefined ? undefined : JSON.stringify(init.body),
    cache: "no-store",
  });
  if (!res.ok) {
    const problem = await res.json().catch(() => ({}));
    throw new ApiError(
      res.status,
      problem.detail ?? `Request failed (${res.status})`,
    );
  }
  return res.status === 204 ? (undefined as T) : res.json();
}

export const login = (email: string, password: string) =>
  request<Tokens>("/api/auth/login", {
    method: "POST",
    body: { email, password },
  });

export const register = (email: string, password: string, fullName: string) =>
  request<User>("/api/auth/register", {
    method: "POST",
    body: { email, password, fullName },
  });

export type SetupStatus = { setupRequired: boolean };

export const getSetupStatus = () => request<SetupStatus>("/api/setup/status");

/** Creates the system owner. Only works once, on a fresh system. */
export const setupOwner = (email: string, password: string, fullName: string) =>
  request<User>("/api/setup", {
    method: "POST",
    body: { email, password, fullName },
  });

export type InvitationPreview = {
  email: string;
  organizationName: string;
  role: string;
  inviterName: string;
  /** True when the invited email already has an account, so the person signs in instead. */
  accountExists: boolean;
};

export const previewInvitation = (token: string) =>
  request<InvitationPreview>("/api/invitations/preview", {
    method: "POST",
    body: { token },
  });

/** Creates the account for an invited email and joins the organization; returns a session. */
export const acceptInvitationAsNewUser = (
  token: string,
  fullName: string,
  password: string,
) =>
  request<Tokens>("/api/invitations/accept-new", {
    method: "POST",
    body: { token, fullName, password },
  });

export const refresh = (refreshToken: string) =>
  request<Tokens>("/api/auth/refresh", {
    method: "POST",
    body: { refreshToken },
  });

export const logout = (refreshToken: string) =>
  request<void>("/api/auth/logout", {
    method: "POST",
    body: { refreshToken },
  });

export const fetchMe = (accessToken: string) =>
  request<User>("/api/auth/me", { accessToken });

export async function getBackendStatus(): Promise<string> {
  const res = await fetch(`${apiBaseUrl()}/actuator/health`);
  if (!res.ok) throw new Error(`Backend returned ${res.status}`);
  return (await res.json()).status;
}
