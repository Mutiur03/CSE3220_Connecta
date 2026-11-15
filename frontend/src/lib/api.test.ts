import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, fetchMe, login, logout, refresh } from "./api";

const user = {
  id: "1",
  email: "jane@example.com",
  fullName: "Jane",
  roles: ["AGENT"],
};
const tokens = {
  tokenType: "Bearer",
  accessToken: "a",
  expiresIn: 900,
  refreshToken: "r",
  user,
};

function respond(status: number, body?: unknown) {
  const fetchMock = vi.fn().mockResolvedValue(
    new Response(body === undefined ? null : JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" },
    }),
  );
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

afterEach(() => vi.unstubAllGlobals());

describe("api client", () => {
  it("login posts the credentials as JSON and returns the tokens", async () => {
    const fetchMock = respond(200, tokens);

    await expect(login("jane@example.com", "secret")).resolves.toEqual(tokens);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/api\/auth\/login$/);
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body)).toEqual({
      email: "jane@example.com",
      password: "secret",
    });
    expect(init.headers["Content-Type"]).toBe("application/json");
  });

  it("turns a problem response into an ApiError with the status and detail", async () => {
    respond(401, { status: 401, detail: "Invalid email or password" });

    await expect(login("jane@example.com", "bad")).rejects.toMatchObject({
      name: "Error",
      status: 401,
      message: "Invalid email or password",
    });
    await expect(login("jane@example.com", "bad")).rejects.toBeInstanceOf(
      ApiError,
    );
  });

  it("copes with an error response that is not JSON", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response("Bad Gateway", { status: 502 })),
    );

    await expect(login("a@b.co", "x")).rejects.toMatchObject({
      status: 502,
      message: "Request failed (502)",
    });
  });

  it("fetchMe sends the access token as a bearer header", async () => {
    const fetchMock = respond(200, user);

    await expect(fetchMe("access-token")).resolves.toEqual(user);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/api\/auth\/me$/);
    expect(init.headers.Authorization).toBe("Bearer access-token");
  });

  it("refresh posts the refresh token", async () => {
    const fetchMock = respond(200, tokens);

    await refresh("old-refresh");

    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
      refreshToken: "old-refresh",
    });
  });

  it("logout resolves on 204 with no body", async () => {
    const fetchMock = respond(204);

    await expect(logout("some-refresh")).resolves.toBeUndefined();
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
      refreshToken: "some-refresh",
    });
  });

  it("never caches responses", async () => {
    const fetchMock = respond(200, user);

    await fetchMe("t");

    expect(fetchMock.mock.calls[0][1].cache).toBe("no-store");
  });
});
