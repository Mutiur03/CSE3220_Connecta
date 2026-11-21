import { renderToStaticMarkup } from "react-dom/server";
import { beforeEach, describe, expect, it, vi } from "vitest";

const pathname = vi.hoisted(() => ({ current: "/" }));
vi.mock("next/navigation", () => ({ usePathname: () => pathname.current }));

import RailOrgSection from "./rail-org-section";

const FIRST_ID = "11111111-2222-3333-4444-555555555555";
const SECOND_ID = "66666666-7777-8888-9999-000000000000";
const organizations = [
  { id: FIRST_ID, name: "Northwind Coffee" },
  { id: SECOND_ID, name: "Contoso Books" },
];

const render = (remembered?: string, orgs = organizations) =>
  renderToStaticMarkup(<RailOrgSection organizations={orgs} remembered={remembered} />);

beforeEach(() => {
  pathname.current = "/";
});

describe("RailOrgSection", () => {
  it("shows the first organization's Products link on Home and the organizations page", () => {
    for (const path of ["/", "/organizations", "/admin/organizations"]) {
      pathname.current = path;
      const markup = render();
      expect(markup).toContain("Northwind Coffee");
      expect(markup).toContain(`href="/organizations/${FIRST_ID}/products"`);
      expect(markup).toContain(">Products<");
    }
  });

  it("uses the remembered organization outside org routes", () => {
    const markup = render(SECOND_ID);
    expect(markup).toContain("Contoso Books");
    expect(markup).toContain(`href="/organizations/${SECOND_ID}/products"`);
    expect(markup).not.toContain(FIRST_ID);
  });

  it("uses the organization in the URL over the remembered one", () => {
    pathname.current = `/organizations/${FIRST_ID}`;
    const markup = render(SECOND_ID);
    expect(markup).toContain(`href="/organizations/${FIRST_ID}/products"`);
  });

  it("renders nothing when the person belongs to no organization", () => {
    pathname.current = "/";
    expect(render(undefined, [])).toBe("");
  });

  it("marks the Products link as the current page on the products route", () => {
    pathname.current = `/organizations/${FIRST_ID}/products`;
    expect(render()).toMatch(/aria-current="page"[^>]*>|href="[^"]*\/products"[^>]*aria-current="page"/);
  });

  it("does not mark Products active on the organization overview", () => {
    pathname.current = `/organizations/${FIRST_ID}`;
    expect(render()).not.toContain('aria-current="page"');
  });

  it("links Inventory after Categories and marks it active on the inventory route", () => {
    pathname.current = `/organizations/${FIRST_ID}/inventory`;
    const markup = render();
    expect(markup).toContain(`href="/organizations/${FIRST_ID}/inventory"`);
    expect(markup).toContain(">Inventory<");
    expect(markup.match(/aria-current="page"/g)).toHaveLength(1);
    expect(markup).not.toMatch(/href="[^"]*\/products"[^>]*aria-current="page"/);
  });

  it("does not mark Inventory active on the products route", () => {
    pathname.current = `/organizations/${FIRST_ID}/products`;
    const markup = render();
    expect(markup).toContain(`href="/organizations/${FIRST_ID}/inventory"`);
    expect(markup).not.toMatch(/href="[^"]*\/inventory"[^>]*aria-current="page"/);
  });

  it("links Categories and marks it, not Products, active on the categories route", () => {
    pathname.current = `/organizations/${FIRST_ID}/categories/new`;
    const markup = render();
    expect(markup).toContain(`href="/organizations/${FIRST_ID}/categories"`);
    expect(markup.match(/aria-current="page"/g)).toHaveLength(1);
    expect(markup).toMatch(/aria-current="page"[^>]*class|href="[^"]*\/categories"[^>]*aria-current="page"/);
    expect(markup).not.toMatch(/href="[^"]*\/products"[^>]*aria-current="page"/);
  });
});
