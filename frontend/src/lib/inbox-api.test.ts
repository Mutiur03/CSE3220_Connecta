import { afterEach, describe, expect, it, vi } from "vitest";
import {
  getConversation,
  listMessages,
  updateConversationStatus,
} from "./inbox-api";

function respond(body: unknown) {
  const fetchMock = vi.fn().mockResolvedValue(
    new Response(JSON.stringify(body), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }),
  );
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

afterEach(() => vi.unstubAllGlobals());

describe("conversation thread api", () => {
  it("gets one conversation, bearer authenticated", async () => {
    const fetchMock = respond({ id: "c1" });
    await getConversation("tok", "o1", "c1");
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/api\/organizations\/o1\/conversations\/c1$/);
    expect(init.method).toBe("GET");
    expect(init.headers.Authorization).toBe("Bearer tok");
  });

  it("asks for a page of messages with page and size", async () => {
    const fetchMock = respond({ content: [] });
    await listMessages("tok", "o1", "c1", 2, 30);
    expect(fetchMock.mock.calls[0][0]).toMatch(
      /\/conversations\/c1\/messages\?page=2&size=30$/,
    );
  });

  it("patches the status as JSON", async () => {
    const fetchMock = respond({ id: "c1" });
    await updateConversationStatus("tok", "o1", "c1", "CLOSED");
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/conversations\/c1$/);
    expect(init.method).toBe("PATCH");
    expect(JSON.parse(init.body)).toEqual({ status: "CLOSED" });
  });
});
