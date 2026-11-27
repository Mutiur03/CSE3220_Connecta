import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { LocalTime, LocalTitled } from "./local-time";

const MOMENT = "2026-11-27T22:03:00Z";

describe("LocalTime on the server", () => {
  // The server cannot know the reader's zone, so it renders UTC; the browser swaps in local time after hydration.
  it("renders a <time> carrying the ISO value, with the text in UTC", () => {
    const html = renderToStaticMarkup(<LocalTime iso={MOMENT} kind="time" />);

    expect(html).toMatch(
      /^<time dateTime="2026-11-27T22:03:00Z">10:03\sPM<\/time>$/,
    );
  });

  it.each([
    ["date", "Nov 27, 2026"],
    ["dateTime", "Nov 27, 2026"],
  ] as const)("prints the %s kind", (kind, expected) => {
    expect(
      renderToStaticMarkup(<LocalTime iso={MOMENT} kind={kind} />),
    ).toContain(expected);
  });

  it("prints its children and only the tooltip in local time when no kind is given", () => {
    const html = renderToStaticMarkup(
      <LocalTime iso={MOMENT} titleKind="dateTime" className="x">
        5 minutes ago
      </LocalTime>,
    );

    expect(html).toContain(">5 minutes ago<");
    expect(html).toContain('title="Nov 27, 2026');
    expect(html).toContain('class="x"');
  });

  it("sets no title when none is asked for", () => {
    expect(
      renderToStaticMarkup(<LocalTime iso={MOMENT} kind="date" />),
    ).not.toContain("title=");
  });
});

describe("LocalTitled", () => {
  it("is a div whose tooltip is the date and clock of the moment", () => {
    const html = renderToStaticMarkup(
      <LocalTitled iso={MOMENT} className="bubble">
        hello
      </LocalTitled>,
    );

    expect(html).toMatch(
      /^<div title="Nov 27, 2026[^"]*10:03\sPM" class="bubble">hello<\/div>$/,
    );
  });
});
