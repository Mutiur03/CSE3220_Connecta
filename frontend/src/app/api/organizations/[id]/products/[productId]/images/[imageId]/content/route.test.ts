import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { GET } from "./route";

const ORG = "00000000-0000-0000-0000-000000000001";
const PRODUCT = "11111111-1111-1111-1111-111111111111";
const IMAGE = "44444444-4444-4444-4444-444444444444";
const ORIGIN = "http://localhost:3000";

const context = (imageId = IMAGE) => ({
  params: Promise.resolve({ id: ORG, productId: PRODUCT, imageId }),
});

function request(cookie?: string): NextRequest {
  return new NextRequest(`${ORIGIN}/api/organizations/${ORG}/products/${PRODUCT}/images/${IMAGE}/content`, {
    headers: cookie ? { cookie } : undefined,
  });
}

describe("GET image content proxy", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("answers 401 without calling the backend when there is no access cookie", async () => {
    const response = await GET(request(), context());

    expect(response.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("forwards the access cookie as a bearer token and streams the image back", async () => {
    fetchMock.mockResolvedValue(
      new Response("bytes", { status: 200, headers: { "content-type": "image/webp" } }),
    );

    const response = await GET(request("access_token=abc123"), context());

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain(`/api/organizations/${ORG}/products/${PRODUCT}/images/${IMAGE}/content`);
    expect(init.headers).toEqual({ Authorization: "Bearer abc123" });
    expect(response.status).toBe(200);
    expect(response.headers.get("content-type")).toBe("image/webp");
    expect(response.headers.get("cache-control")).toBe("private, max-age=3600");
    expect(response.headers.get("x-content-type-options")).toBe("nosniff");
  });

  it("passes a backend 404 through and turns a backend 500 into 502", async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 404 }));
    expect((await GET(request("access_token=abc"), context())).status).toBe(404);

    fetchMock.mockResolvedValueOnce(new Response(null, { status: 500 }));
    expect((await GET(request("access_token=abc"), context())).status).toBe(502);
  });

  it("answers 404 for a malformed id without calling the backend", async () => {
    const response = await GET(request("access_token=abc"), context("not-a-uuid"));

    expect(response.status).toBe(404);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
