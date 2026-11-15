// Base URL of the backend REST API. Set NEXT_PUBLIC_API_URL in frontend/.env.local to override.
export const API_URL =
  process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export async function getBackendStatus(): Promise<string> {
  const res = await fetch(`${API_URL}/actuator/health`);
  if (!res.ok) throw new Error(`Backend returned ${res.status}`);
  return (await res.json()).status;
}
