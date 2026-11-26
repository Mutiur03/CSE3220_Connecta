import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import InboxAssigneeTabs from "./inbox-assignee-tabs";

const LIST = "/organizations/org-1/inbox";
const html = (
  filters: Parameters<typeof InboxAssigneeTabs>[0]["filters"],
  unassignedCount: number | null = 4,
) =>
  renderToStaticMarkup(
    <InboxAssigneeTabs
      listPath={LIST}
      filters={filters}
      unassignedCount={unassignedCount}
    />,
  );

describe("InboxAssigneeTabs", () => {
  it("marks Everyone by default and links the others, keeping the other filters", () => {
    const markup = html({ q: "ana", tab: "CLOSED" });
    expect(markup).toMatch(
      /<a aria-current="page"[^>]*inbox\?q=ana&amp;status=CLOSED"/,
    );
    expect(markup).toContain(
      `href="${LIST}?q=ana&amp;assignee=me&amp;status=CLOSED"`,
    );
    expect(markup).toContain(
      `href="${LIST}?q=ana&amp;assignee=none&amp;status=CLOSED"`,
    );
    expect(markup.match(/aria-current="page"/g)).toHaveLength(1);
  });

  it("marks the chosen option and shows the unassigned count in its name", () => {
    const markup = html({ assignee: "none" });
    expect(markup).toMatch(
      /<a aria-current="page"[^>]*assignee=none"[^>]*>Unassigned \(4\)</,
    );
  });

  it("drops the count when it is unknown", () => {
    expect(html({}, null)).toContain(">Unassigned<");
  });
});
