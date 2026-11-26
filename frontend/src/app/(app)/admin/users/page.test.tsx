import { renderToStaticMarkup } from "react-dom/server";
import { beforeEach, describe, expect, it, vi } from "vitest";

const session = vi.hoisted(() => ({
  getAccessToken: vi.fn(),
  getCurrentUser: vi.fn(),
  redirectOnUnauthorized: vi.fn(),
}));
const api = vi.hoisted(() => ({ listUsers: vi.fn() }));

vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));
vi.mock("next/navigation", () => ({
  notFound: vi.fn(() => {
    throw new Error("NOT_FOUND");
  }),
  redirect: vi.fn(),
}));
vi.mock("@/lib/session", () => session);
vi.mock("@/lib/user-api", () => api);

import { ApiError } from "@/lib/api";
import UsersPage from "./page";

type Element = { type: unknown; props: { searchParams: Promise<object> } };

/** Page renders a header plus an async Users component; run the latter directly. */
async function renderUsers() {
  const main = UsersPage({ searchParams: Promise.resolve({}) }) as unknown as {
    props: { children: Element[] };
  };
  const users = main.props.children[1];
  const Users = users.type as (p: Element["props"]) => Promise<React.ReactNode>;
  return renderToStaticMarkup(<>{await Users(users.props)}</>);
}

beforeEach(() => {
  vi.clearAllMocks();
  session.getAccessToken.mockResolvedValue("access");
  session.getCurrentUser.mockResolvedValue({ id: "me", roles: ["OWNER"] });
});

describe("/admin/users", () => {
  it("is not found for a non-owner and never calls the API", async () => {
    session.getCurrentUser.mockResolvedValue({ id: "x", roles: ["ADMIN"] });
    await expect(renderUsers()).rejects.toThrow("NOT_FOUND");
    expect(api.listUsers).not.toHaveBeenCalled();
  });

  it("lists users for the owner", async () => {
    api.listUsers.mockResolvedValue({
      content: [
        {
          id: "a",
          email: "a@x.io",
          fullName: "Ann",
          enabled: true,
          roles: ["ADMIN"],
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
    });
    const html = await renderUsers();
    expect(html).toContain("a@x.io");
    expect(api.listUsers).toHaveBeenCalledWith("access", 0, 20);
  });

  it("shows an empty state", async () => {
    api.listUsers.mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
    });
    expect(await renderUsers()).toContain("No users yet.");
  });

  it("shows an error state when the API fails", async () => {
    api.listUsers.mockRejectedValue(new ApiError(500, "boom"));
    expect(await renderUsers()).toContain("Something went wrong");
  });
});
