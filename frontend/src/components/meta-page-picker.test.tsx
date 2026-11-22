import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import type { MetaAvailablePage } from "@/lib/meta-api";
import MetaPagePicker from "./meta-page-picker";

const noop = async () => ({});

const pages: MetaAvailablePage[] = [
  { pageId: "p1", name: "Shop Dhaka", connected: true },
  { pageId: "p2", name: "Shop Sylhet", connected: false },
];

const html = (list: MetaAvailablePage[]) =>
  renderToStaticMarkup(<MetaPagePicker pages={list} action={noop} />);

describe("MetaPagePicker", () => {
  it("lists each Page, and marks the connected ones as done", () => {
    const markup = html(pages);

    expect(markup).toContain("Shop Dhaka");
    expect(markup).toContain("Shop Sylhet");
    expect(markup).toContain("Connected");
    expect(markup).toContain("Not connected");
  });

  it("disables a connected Page's checkbox so it cannot be ticked again", () => {
    const tags = html(pages).match(/<input[^>]*>/g) ?? [];
    const tagFor = (pageId: string) =>
      tags.find((tag) => tag.includes(`value="${pageId}"`)) ?? "";

    expect(tagFor("p1")).toContain('disabled=""');
    expect(tagFor("p2")).not.toContain('disabled=""');
  });

  it("disables the submit when every Page is already connected", () => {
    const markup = html([
      { pageId: "p1", name: "Shop Dhaka", connected: true },
    ]);

    expect(markup).toMatch(
      /<button[^>]*disabled=""[^>]*type="submit"|<button[^>]*type="submit"[^>]*disabled=""/,
    );
  });
});
