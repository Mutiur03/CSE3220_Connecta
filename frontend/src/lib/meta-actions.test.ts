import { beforeEach, describe, expect, it, vi } from "vitest";

const redirect = vi.hoisted(() =>
  vi.fn((url: string) => {
    throw new Error(`NEXT_REDIRECT ${url}`);
  }),
);
const revalidatePath = vi.hoisted(() => vi.fn());
const api = vi.hoisted(() => ({
  startMetaConnection: vi.fn(),
  startInstagramConnection: vi.fn(),
  connectPages: vi.fn(),
}));
const session = vi.hoisted(() => ({ getAccessToken: vi.fn() }));

vi.mock("next/navigation", () => ({ redirect }));
vi.mock("next/cache", () => ({ revalidatePath }));
vi.mock("./session", () => session);
vi.mock("./meta-api", () => api);

import { ApiError } from "./api";
import { actionErrorMessage } from "./org-errors";
import {
  connectPagesAction,
  startFacebookConnection,
  startInstagramConnection,
} from "./meta-actions";

const ORG = "11111111-1111-1111-1111-111111111111";

const formWith = (...pageIds: string[]) => {
  const formData = new FormData();
  for (const id of pageIds) formData.append("pageId", id);
  return formData;
};

beforeEach(() => {
  vi.clearAllMocks();
  session.getAccessToken.mockResolvedValue("token");
});

describe("connectPagesAction", () => {
  it("asks for a Page when none was ticked, without calling the API", async () => {
    const state = await connectPagesAction(ORG, {}, formWith());

    expect(state).toEqual({ message: "Choose at least one Page to connect." });
    expect(api.connectPages).not.toHaveBeenCalled();
  });

  it("connects the ticked Pages once each and reports how many", async () => {
    api.connectPages.mockResolvedValue([{ pageId: "p1" }, { pageId: "p2" }]);

    const state = await connectPagesAction(ORG, {}, formWith("p1", "p2", "p1"));

    expect(api.connectPages).toHaveBeenCalledWith("token", ORG, ["p1", "p2"]);
    expect(state).toEqual({ notice: "2 Pages connected." });
    expect(revalidatePath).toHaveBeenCalledWith(
      `/organizations/${ORG}/integrations`,
    );
  });

  it("shows the backend's message when the connection fails", async () => {
    api.connectPages.mockRejectedValue(
      new ApiError(422, "Page is not part of the Meta grant"),
    );

    const state = await connectPagesAction(ORG, {}, formWith("p9"));

    expect(state).toEqual({ message: "Page is not part of the Meta grant" });
    expect(revalidatePath).not.toHaveBeenCalled();
  });
});

describe("startFacebookConnection", () => {
  it("sends the browser to the Meta login URL", async () => {
    api.startMetaConnection.mockResolvedValue({
      authorizationUrl: "https://www.facebook.com/dialog",
    });

    await expect(startFacebookConnection(ORG)).rejects.toThrow(
      "NEXT_REDIRECT https://www.facebook.com/dialog",
    );
    expect(redirect).toHaveBeenCalledWith("https://www.facebook.com/dialog");
  });

  it("stays on the page and shows the message when Meta is not configured", async () => {
    api.startMetaConnection.mockRejectedValue(
      new ApiError(503, "Meta is not configured on this server"),
    );

    const state = await startFacebookConnection(ORG);

    expect(state).toEqual({
      message: actionErrorMessage(
        new ApiError(503, "Meta is not configured on this server"),
      ),
    });
    expect(redirect).not.toHaveBeenCalled();
  });
});

describe("startInstagramConnection", () => {
  it("sends the browser to the Instagram login URL", async () => {
    api.startInstagramConnection.mockResolvedValue({
      authorizationUrl: "https://www.instagram.com/oauth",
    });

    await expect(startInstagramConnection(ORG)).rejects.toThrow(
      "NEXT_REDIRECT https://www.instagram.com/oauth",
    );
  });
});
