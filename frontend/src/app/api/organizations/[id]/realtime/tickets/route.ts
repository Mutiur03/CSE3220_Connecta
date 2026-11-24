import { NextRequest, NextResponse } from "next/server";
import { request as apiRequest, ApiError } from "@/lib/api";
import { isUuid } from "@/lib/org-validation";
import { ACCESS_COOKIE } from "@/lib/session-cookies";

type Params = Promise<{ id: string }>;
type Ticket = { ticket: string; expiresInSeconds: number };

const BACKEND_FAILURE = 502;

// Gives the browser a single-use WebSocket ticket. The access token stays in its httpOnly cookie:
// this route adds it server-side and returns only the ticket, never the token.
export async function POST(
  request: NextRequest,
  { params }: { params: Params },
) {
  const accessToken = request.cookies.get(ACCESS_COOKIE)?.value;
  if (!accessToken) return new NextResponse(null, { status: 401 });

  const { id } = await params;
  if (!isUuid(id)) return new NextResponse(null, { status: 404 });

  try {
    const { ticket, expiresInSeconds } = await apiRequest<Ticket>(
      `/api/organizations/${id}/realtime/tickets`,
      { method: "POST", accessToken },
    );
    return NextResponse.json(
      { ticket, expiresInSeconds },
      { headers: { "Cache-Control": "no-store" } },
    );
  } catch (error) {
    const status =
      error instanceof ApiError && error.status < 500
        ? error.status
        : BACKEND_FAILURE;
    return new NextResponse(null, { status });
  }
}
