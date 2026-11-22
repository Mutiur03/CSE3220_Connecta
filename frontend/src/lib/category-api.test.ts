import { afterEach, describe, expect, it, vi } from "vitest";
import { listAllCategories, type Category } from "./category-api";
import { ApiError } from "./api";

const PAGE_SIZE = 100;

function category(index: number): Category {
  return {
    id: `cat-${index}`,
    name: `Category ${index}`,
    description: null,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  };
}

/** Serves the organization's categories as the backend pages them, keyed by the page query. */
function serveCategories(
  total: number,
  overrides: Record<number, Category[]> = {},
) {
  const fetchMock = vi.fn(async (url: string) => {
    const page = Number(new URL(url).searchParams.get("page"));
    const first = page * PAGE_SIZE;
    const content =
      overrides[page] ??
      Array.from(
        { length: Math.max(0, Math.min(PAGE_SIZE, total - first)) },
        (_, i) => category(first + i),
      );
    return new Response(
      JSON.stringify({ content, page, size: PAGE_SIZE, totalElements: total }),
      { status: 200, headers: { "Content-Type": "application/json" } },
    );
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

afterEach(() => vi.unstubAllGlobals());

describe("listAllCategories", () => {
  it("reads a single page without asking for more", async () => {
    const fetchMock = serveCategories(3);

    const categories = await listAllCategories("tok", "org-1");

    expect(categories.map((c) => c.id)).toEqual(["cat-0", "cat-1", "cat-2"]);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toMatch(/page=0&size=100$/);
  });

  it("returns an empty list for an organization with no categories", async () => {
    const fetchMock = serveCategories(0);

    await expect(listAllCategories("tok", "org-1")).resolves.toEqual([]);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("follows every page past the first 100 and keeps the backend order", async () => {
    const fetchMock = serveCategories(250);

    const categories = await listAllCategories("tok", "org-1");

    expect(categories).toHaveLength(250);
    expect(categories[0].id).toBe("cat-0");
    expect(categories[100].id).toBe("cat-100");
    expect(categories[249].id).toBe("cat-249");
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it("fetches exactly one request per page, with no extra page past the total", async () => {
    const fetchMock = serveCategories(200);

    const categories = await listAllCategories("tok", "org-1");

    expect(categories).toHaveLength(200);
    const pages = fetchMock.mock.calls.map(([url]) =>
      new URL(url).searchParams.get("page"),
    );
    expect(pages.sort()).toEqual(["0", "1"]);
  });

  it("fails instead of looping when a later page comes back empty", async () => {
    serveCategories(250, { 1: [] });

    await expect(listAllCategories("tok", "org-1")).rejects.toBeInstanceOf(
      ApiError,
    );
  });

  it("fails when the first page is empty but the total says rows exist", async () => {
    serveCategories(150, { 0: [] });

    await expect(listAllCategories("tok", "org-1")).rejects.toThrow(
      "Category list ended before the reported total",
    );
  });
});
