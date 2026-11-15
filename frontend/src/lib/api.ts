// Base URL of the backend REST API. Set NEXT_PUBLIC_API_URL in frontend/.env.local to override.
export const API_URL =
  process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

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

async function request<T>(
  path: string,
  init: { method?: string; body?: unknown; accessToken?: string } = {},
): Promise<T> {
  const res = await fetch(`${API_URL}${path}`, {
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
  const res = await fetch(`${API_URL}/actuator/health`);
  if (!res.ok) throw new Error(`Backend returned ${res.status}`);
  return (await res.json()).status;
}
