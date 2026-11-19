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
  it("returns a name error without calling the API", async () => {
    const state = await createOrganization({}, form({ name: "" }));
    expect(state.errors?.name).toBeDefined();
    expect(api.createOrganization).not.toHaveBeenCalled();
  });

  it("rejects a name with no letters or digits", async () => {
    const state = await createOrganization({}, form({ name: "!!!" }));
    expect(state.errors?.name).toBe("Use at least one letter or digit.");
  });

  it("derives the slug from the name and redirects on success", async () => {
    api.createOrganization.mockResolvedValue({ id: ORG_ID });
    await expect(
      createOrganization({}, form({ name: "Acme Studio" })),
    ).rejects.toThrow(`NEXT_REDIRECT /organizations/${ORG_ID}`);
    expect(api.createOrganization).toHaveBeenCalledWith("access", {
      name: "Acme Studio",
      slug: "acme-studio",
    });
  });

  it("retries with a short suffix when the slug is taken", async () => {
    api.createOrganization
      .mockRejectedValueOnce(new ApiError(409, "Slug is taken"))
      .mockResolvedValueOnce({ id: ORG_ID });
    await expect(
      createOrganization({}, form({ name: "Acme" })),
    ).rejects.toThrow(`NEXT_REDIRECT /organizations/${ORG_ID}`);
    const retry = api.createOrganization.mock.calls[1][1];
    expect(retry.slug).toMatch(/^acme-[a-z0-9]{1,4}$/);
  });

  it("shows the backend detail for any other failure", async () => {
    api.createOrganization.mockRejectedValue(new ApiError(422, "Not allowed"));
    const state = await createOrganization({}, form({ name: "Acme" }));
    expect(state).toEqual({ message: "Not allowed", values: { name: "Acme" } });
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
