import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import InboxStatusTabs from "./inbox-status-tabs";

const LIST = "/organizations/org-1/inbox";

describe("InboxStatusTabs", () => {
  it("marks Open by default and links each tab keeping the search", () => {
    const markup = renderToStaticMarkup(
      <InboxStatusTabs listPath={LIST} filters={{ q: "ana" }} />,
    );
    expect(markup).toMatch(/<a aria-current="page"[^>]*inbox\?q=ana"/);
    expect(markup).toContain(`href="${LIST}?q=ana&amp;status=CLOSED"`);
    expect(markup).toContain(`href="${LIST}?q=ana&amp;status=ALL"`);
  });

  it("marks the Closed tab when it is chosen", () => {
    const markup = renderToStaticMarkup(
      <InboxStatusTabs listPath={LIST} filters={{ tab: "CLOSED" }} />,
    );
    expect(markup.match(/aria-current="page"/g)).toHaveLength(1);
    expect(markup).toMatch(/<a aria-current="page"[^>]*status=CLOSED"/);
  });
});
