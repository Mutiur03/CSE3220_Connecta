import { NextResponse } from "next/server";

/**
 * 307 with a relative Location. Behind the container, request.url carries the bind address
 * (0.0.0.0), so an absolute URL built from it is unreachable for the browser.
 */
export function redirectTo(path: string): NextResponse {
  return new NextResponse(null, { status: 307, headers: { Location: path } });
}
