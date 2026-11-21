import { renderToStaticMarkup } from "react-dom/server";
import { beforeEach, describe, expect, it, vi } from "vitest";

const pathname = vi.hoisted(() => ({ current: "/" }));
vi.mock("next/navigation", () => ({ usePathname: () => pathname.current }));

import RailOrgSection, { orgIdFromPath } from "./rail-org-section";

const ORG_ID = "11111111-2222-3333-4444-555555555555";
const organizations = [{ id: ORG_ID, name: "Northwind Coffee" }];

const render = () => renderToStaticMarkup(<RailOrgSection organizations={organizations} />);

beforeEach(() => {
  pathname.current = "/";
});

describe("RailOrgSection", () => {
  it("renders nothing outside an organization route", () => {
    for (const path of ["/", "/organizations", "/admin/organizations"]) {
      pathname.current = path;
      expect(render()).toBe("");
    }
  });

  it("shows the organization's name and a Products link inside its route", () => {
    pathname.current = `/organizations/${ORG_ID}`;
    const markup = render();
    expect(markup).toContain("Northwind Coffee");
    expect(markup).toContain(`href="/organizations/${ORG_ID}/products"`);
    expect(markup).toContain(">Products<");
  });

  it("marks the Products link as the current page on the products route", () => {
    pathname.current = `/organizations/${ORG_ID}/products`;
    expect(render()).toMatch(/aria-current="page"[^>]*>|href="[^"]*\/products"[^>]*aria-current="page"/);
  });

  it("does not mark Products active on the organization overview", () => {
    pathname.current = `/organizations/${ORG_ID}`;
    expect(render()).not.toContain('aria-current="page"');
  });

  it("falls back to a generic name when the organization is not in the list", () => {
    pathname.current = `/organizations/${ORG_ID}/products`;
    expect(renderToStaticMarkup(<RailOrgSection organizations={[]} />)).toContain(
      "Organization",
    );
  });
});

describe("orgIdFromPath", () => {
  it("reads the id from organization paths only", () => {
    expect(orgIdFromPath(`/organizations/${ORG_ID}/products/new`)).toBe(ORG_ID);
    expect(orgIdFromPath(`/organizations/${ORG_ID}`)).toBe(ORG_ID);
    expect(orgIdFromPath("/organizations")).toBeUndefined();
    expect(orgIdFromPath("/organizations/not-a-uuid/products")).toBeUndefined();
  });
});
