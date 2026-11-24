import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import InboxFilterBar from "./inbox-filter-bar";

const LIST = "/organizations/org-1/inbox";
const html = (filters: Parameters<typeof InboxFilterBar>[0]["filters"]) =>
  renderToStaticMarkup(<InboxFilterBar listPath={LIST} filters={filters} />);

describe("InboxFilterBar", () => {
  it("is a GET form to the list with search and channel fields", () => {
    const markup = html({});
    expect(markup).toContain('method="get"');
    expect(markup).toContain(`action="${LIST}"`);
    expect(markup).toContain('name="q"');
    expect(markup).toContain('name="channel"');
    expect(markup).toContain(">Instagram</option>");
    expect(markup).toContain(">Messenger</option>");
  });

  it("keeps the current search and channel", () => {
    const markup = html({ q: "ana", channel: "MESSENGER" });
    expect(markup).toMatch(/name="q"[^>]*value="ana"/);
    expect(markup).toMatch(/value="MESSENGER"[^>]*selected/);
  });

  it("shows clear and reset only when filtered, clearing just the search", () => {
    expect(html({})).not.toContain("Reset filters");
    const markup = html({ q: "ana", channel: "MESSENGER" });
    expect(markup).toContain("Reset filters");
    expect(markup).toContain(`href="${LIST}?channel=MESSENGER"`);
  });
});
