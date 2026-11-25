import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { POST } from "./route";

const ORG = "00000000-0000-0000-0000-000000000001";
const context = (id = ORG) => ({ params: Promise.resolve({ id }) });

const request = (cookie?: string) =>
  new NextRequest(
    `http://localhost:3000/api/organizations/${ORG}/realtime/tickets`,
    {
      method: "POST",
      headers: cookie ? { cookie } : undefined,
    },
  );

describe("POST realtime ticket proxy", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => vi.unstubAllGlobals());

  it("answers 401 without calling the backend when there is no access cookie", async () => {
    expect((await POST(request(), context())).status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("answers 404 for a malformed id", async () => {
    expect((await POST(request("access_token=abc"), context("x"))).status).toBe(
      404,
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("adds the bearer token server-side and returns only the ticket", async () => {
    fetchMock.mockResolvedValue(
      Response.json({ ticket: "tkt", expiresInSeconds: 30 }, { status: 201 }),
    );

    const response = await POST(
      request("access_token=secret-token"),
      context(),
    );

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain(`/api/organizations/${ORG}/realtime/tickets`);
    expect(init.method).toBe("POST");
    expect(init.headers.Authorization).toBe("Bearer secret-token");
    expect(response.status).toBe(200);
    expect(response.headers.get("cache-control")).toBe("no-store");
    const text = await response.text();
    expect(JSON.parse(text)).toEqual({ ticket: "tkt", expiresInSeconds: 30 });
    expect(text).not.toContain("secret-token");
  });

  it("passes backend 401 and 404 through and turns a 500 into 502", async () => {
    for (const [from, to] of [
      [401, 401],
      [404, 404],
      [500, 502],
    ]) {
      fetchMock.mockResolvedValueOnce(
        Response.json({ detail: "x" }, { status: from }),
      );
      expect((await POST(request("access_token=abc"), context())).status).toBe(
        to,
      );
    }
  });
});
