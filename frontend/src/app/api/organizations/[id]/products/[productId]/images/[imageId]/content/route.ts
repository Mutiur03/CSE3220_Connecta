import { NextRequest, NextResponse } from "next/server";
import { apiBaseUrl } from "@/lib/api";
import { isUuid } from "@/lib/org-validation";
import { ACCESS_COOKIE } from "@/lib/session-cookies";

type Params = Promise<{ id: string; productId: string; imageId: string }>;

const BACKEND_FAILURE = 502;
const PRIVATE_CACHE = "private, max-age=3600";

// Streams one product image from the authenticated backend endpoint. The browser cannot send the
// bearer token on an <img>, so this route reads the access cookie (httpOnly, never sent to the page)
// and forwards it. The bytes pass through untouched, with the content type the backend stored.
export async function GET(request: NextRequest, { params }: { params: Params }) {
  const accessToken = request.cookies.get(ACCESS_COOKIE)?.value;
  if (!accessToken) return new NextResponse(null, { status: 401 });

  const { id, productId, imageId } = await params;
  if (![id, productId, imageId].every(isUuid)) return new NextResponse(null, { status: 404 });

  const upstream = await fetch(
    `${apiBaseUrl()}/api/organizations/${id}/products/${productId}/images/${imageId}/content`,
    { headers: { Authorization: `Bearer ${accessToken}` }, cache: "no-store" },
  );
  if (!upstream.ok) {
    // 4xx answers (401, 403, 404) pass through so the caller sees the real reason. Server errors do not.
    const status = upstream.status >= 500 ? BACKEND_FAILURE : upstream.status;
    return new NextResponse(null, { status });
  }

  return new NextResponse(upstream.body, {
    status: 200,
    headers: {
      "Content-Type": upstream.headers.get("content-type") ?? "application/octet-stream",
      "Cache-Control": PRIVATE_CACHE,
      "X-Content-Type-Options": "nosniff",
    },
  });
}
