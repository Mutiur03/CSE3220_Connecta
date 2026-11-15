import { beforeEach, describe, expect, it, vi } from "vitest";

const redirect = vi.hoisted(() =>
  vi.fn((url: string) => {
    throw new Error(`NEXT_REDIRECT ${url}`);
  }),
);
const revalidatePath = vi.hoisted(() => vi.fn());
const api = vi.hoisted(() => ({
  createOrganization: vi.fn(),
  removeMember: vi.fn(),
  createInvitation: vi.fn(),
}));
const session = vi.hoisted(() => ({ getAccessToken: vi.fn() }));

vi.mock("next/navigation", () => ({ redirect }));
vi.mock("next/cache", () => ({ revalidatePath }));
vi.mock("./session", () => session);
vi.mock("./org-api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./org-api")>()),
  ...api,
}));

import { ApiError } from "./api";
import {
  createInvitation,
  createOrganization,
  removeMember,
} from "./org-actions";

function form(values: Record<string, string>) {
  const data = new FormData();
  for (const [key, value] of Object.entries(values)) data.set(key, value);
  return data;
}

const ORG_ID = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";

beforeEach(() => {
  vi.clearAllMocks();
  session.getAccessToken.mockResolvedValue("access");
});

describe("createOrganization", () => {
  it("returns field errors without calling the API", async () => {
    const state = await createOrganization(
      {},
      form({ name: "", slug: "Bad Slug" }),
    );
    expect(state.errors?.name).toBeDefined();
    expect(state.errors?.slug).toBeDefined();
    expect(api.createOrganization).not.toHaveBeenCalled();
  });

  it("redirects to the new organization on success", async () => {
    api.createOrganization.mockResolvedValue({ id: ORG_ID });
    await expect(
      createOrganization({}, form({ name: "Acme", slug: "acme" })),
    ).rejects.toThrow(`NEXT_REDIRECT /organizations/${ORG_ID}`);
    expect(api.createOrganization).toHaveBeenCalledWith("access", {
      name: "Acme",
      slug: "acme",
    });
  });

  it("shows the backend detail when the slug is taken", async () => {
    api.createOrganization.mockRejectedValue(
      new ApiError(409, "Slug is taken"),
    );
    const state = await createOrganization(
      {},
      form({ name: "Acme", slug: "acme" }),
    );
    expect(state).toEqual({
      message: "Slug is taken",
      values: { name: "Acme", slug: "acme" },
    });
  });
});

describe("removeMember", () => {
  it("sends a leaving member back to the organization list", async () => {
    api.removeMember.mockResolvedValue(undefined);
    await expect(removeMember(ORG_ID, "mem-1", true)).rejects.toThrow(
      "NEXT_REDIRECT /organizations",
    );
  });

  it("keeps the admin on the page after removing someone else", async () => {
    api.removeMember.mockResolvedValue(undefined);
    await expect(removeMember(ORG_ID, "mem-1", false)).resolves.toEqual({});
    expect(revalidatePath).toHaveBeenCalledWith(`/organizations/${ORG_ID}`);
  });

  it("returns the message for the last owner's 422", async () => {
    api.removeMember.mockRejectedValue(
      new ApiError(422, "The last owner cannot leave"),
    );
    await expect(removeMember(ORG_ID, "mem-1", true)).resolves.toEqual({
      message: "The last owner cannot leave",
    });
  });
});

describe("createInvitation", () => {
  it("rejects an unknown role before calling the API", async () => {
    const state = await createInvitation(
      ORG_ID,
      {},
      form({ email: "a@b.co", role: "ROOT" }),
    );
    expect(state.errors?.role).toBeDefined();
    expect(api.createInvitation).not.toHaveBeenCalled();
  });

  it("returns the token once, for the form to display", async () => {
    api.createInvitation.mockResolvedValue({
      token: "raw-token",
      invitation: {},
    });
    const state = await createInvitation(
      ORG_ID,
      {},
      form({ email: "a@b.co", role: "AGENT" }),
    );
    expect(state.token).toBe("raw-token");
    expect(state.notice).toBe("Invitation created for a@b.co.");
  });
});
