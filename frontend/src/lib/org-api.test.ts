import { afterEach, describe, expect, it, vi } from "vitest";
import {
  acceptInvitation,
  changeMemberRole,
  createInvitation,
  listInvitations,
  listMembers,
  listOrganizations,
  removeMember,
  revokeInvitation,
} from "./org-api";

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

describe("organization api client", () => {
  it("lists organizations with page and size, bearer authenticated", async () => {
    const fetchMock = respond(200, {
      content: [],
      page: 1,
      size: 20,
      totalElements: 0,
    });

    await listOrganizations("tok", 1);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/api\/organizations\?page=1&size=20$/);
    expect(init.method).toBe("GET");
    expect(init.headers.Authorization).toBe("Bearer tok");
  });

  it("changes a member role with a PATCH body of { role }", async () => {
    const fetchMock = respond(200, {});

    await changeMemberRole("tok", "org-1", "mem-2", "ADMIN");

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/api\/organizations\/org-1\/members\/mem-2$/);
    expect(init.method).toBe("PATCH");
    expect(JSON.parse(init.body)).toEqual({ role: "ADMIN" });
  });

  it("encodes path ids so a crafted id cannot change the route", async () => {
    const fetchMock = respond(204);

    await removeMember("tok", "org/../admin", "mem?x");

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toMatch(
      /\/api\/organizations\/org%2F..%2Fadmin\/members\/mem%3Fx$/,
    );
    expect(init.method).toBe("DELETE");
  });

  it("asks for members and invitations in one page of up to 100", async () => {
    const fetchMock = respond(200, {
      content: [],
      page: 0,
      size: 100,
      totalElements: 0,
    });

    await listMembers("tok", "org-1");

    expect(fetchMock.mock.calls[0][0]).toMatch(/members\?page=0&size=100$/);
  });

  it("passes the requested member page and size", async () => {
    const fetchMock = respond(200, {
      content: [],
      page: 2,
      size: 20,
      totalElements: 0,
    });

    await listMembers("tok", "org-1", 2, 20);

    expect(fetchMock.mock.calls[0][0]).toMatch(/members\?page=2&size=20$/);
  });

  it("passes the requested invitation page and size", async () => {
    const fetchMock = respond(200, {
      content: [],
      page: 1,
      size: 20,
      totalElements: 0,
    });

    await listInvitations("tok", "org-1", 1, 20);

    expect(fetchMock.mock.calls[0][0]).toMatch(/invitations\?page=1&size=20$/);
  });

  it("revokes an invitation with DELETE and resolves on 204", async () => {
    const fetchMock = respond(204);

    await expect(
      revokeInvitation("tok", "org-1", "inv-1"),
    ).resolves.toBeUndefined();
    expect(fetchMock.mock.calls[0][0]).toMatch(/invitations\/inv-1$/);
    expect(fetchMock.mock.calls[0][1].method).toBe("DELETE");
  });

  it("returns the one-time token when an invitation is created", async () => {
    const fetchMock = respond(201, {
      invitation: { id: "i", email: "a@b.co", role: "AGENT" },
      token: "raw-token",
    });

    await expect(
      createInvitation("tok", "org-1", { email: "a@b.co", role: "AGENT" }),
    ).resolves.toMatchObject({ token: "raw-token" });
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
      email: "a@b.co",
      role: "AGENT",
    });
  });

  it("posts the invitation token to accept", async () => {
    const fetchMock = respond(200, { id: "org-1" });

    await acceptInvitation("tok", "raw-token");

    expect(fetchMock.mock.calls[0][0]).toMatch(/\/api\/invitations\/accept$/);
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
      token: "raw-token",
    });
  });
});
