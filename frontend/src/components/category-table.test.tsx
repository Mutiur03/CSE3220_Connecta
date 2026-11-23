import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import type { Category } from "@/lib/category-api";

vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));
vi.mock("next/navigation", () => ({ redirect: vi.fn() }));
vi.mock("@/lib/session", () => ({ getAccessToken: vi.fn() }));

import CategoryTable from "./category-table";

const category: Category = {
  id: "11111111-1111-1111-1111-111111111111",
  name: "Beverages",
  description: null,
  createdAt: "2026-01-02T00:00:00Z",
  updatedAt: "2026-01-02T00:00:00Z",
};

const html = (canManage: boolean, categories = [category]) =>
  renderToStaticMarkup(
    <CategoryTable
      orgId="org-1"
      categories={categories}
      canManage={canManage}
    />,
  );

describe("CategoryTable", () => {
  it("shows edit and delete controls to an ADMIN", () => {
    const markup = html(true);
    expect(markup).toContain('aria-label="Edit Beverages"');
    expect(markup).toContain('aria-label="Delete Beverages"');
    expect(markup).toContain(
      "/organizations/org-1/categories/11111111-1111-1111-1111-111111111111/edit",
    );
  });

  it("shows no edit or delete controls to an AGENT", () => {
    const markup = html(false);
    expect(markup).not.toContain("Edit Beverages");
    expect(markup).not.toContain("Delete Beverages");
    expect(markup).toContain("Beverages");
  });

  it("leaves the description line out when the category has none", () => {
    const markup = html(false);
    expect(markup).toContain("Beverages");
    expect(markup).not.toContain("null");
    expect(markup.match(/<p /g)).toHaveLength(1);
  });

  it("warns in the delete confirmation that products keep existing without a category", () => {
    expect(html(true)).toContain(
      "Its products stay in the catalog without a category.",
    );
  });

  it("names the list for assistive technology", () => {
    expect(html(false)).toContain('<ul aria-label="Categories"');
  });

  it("renders the description when present", () => {
    expect(
      html(false, [{ ...category, description: "Hot and cold drinks" }]),
    ).toContain("Hot and cold drinks");
  });
});
