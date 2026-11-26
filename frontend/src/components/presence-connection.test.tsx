// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { RealtimeClientOptions } from "@/lib/realtime";
import PresenceConnection from "./presence-connection";

const refresh = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh }) }));

const stop = vi.fn();
const startRealtimeClient = vi.fn<
  (options: RealtimeClientOptions) => () => void
>(() => stop);
vi.mock("@/lib/realtime", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/realtime")>()),
  startRealtimeClient: (options: RealtimeClientOptions) =>
    startRealtimeClient(options),
}));

(
  globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

let root: Root;
let container: HTMLDivElement;

const mount = (orgId: string) => {
  container = document.createElement("div");
  document.body.appendChild(container);
  root = createRoot(container);
  act(() => root.render(<PresenceConnection orgId={orgId} />));
};

beforeEach(() => {
  refresh.mockReset();
  stop.mockReset();
  startRealtimeClient.mockClear();
});

afterEach(() => {
  vi.unstubAllGlobals();
  container?.remove();
});

describe("PresenceConnection", () => {
  it("renders nothing and fetches a ticket for its organization", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ ticket: "t1" }),
    });
    vi.stubGlobal("fetch", fetchMock);
    mount("org-1");
    expect(container.innerHTML).toBe("");
    expect(await startRealtimeClient.mock.calls[0][0].fetchTicket()).toBe("t1");
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/organizations/org-1/realtime/tickets",
      { method: "POST" },
    );
  });

  it("never refreshes the page for events or reconnects", () => {
    mount("org-1");
    const options = startRealtimeClient.mock.calls[0][0];
    options.onEvent({
      type: "inbox.refresh",
      conversationId: "",
      messageId: "",
      channel: "",
      direction: "",
      newConversation: false,
    });
    options.onReconnect();
    expect(refresh).not.toHaveBeenCalled();
  });

  it("asks the server to renew an expired session and stops the socket on unmount", () => {
    mount("org-1");
    startRealtimeClient.mock.calls[0][0].onUnauthorized?.();
    expect(refresh).toHaveBeenCalledTimes(1);
    act(() => root.unmount());
    expect(stop).toHaveBeenCalledTimes(1);
  });
});
