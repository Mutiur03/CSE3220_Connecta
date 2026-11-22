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

  it("ticks a connected Page by default and leaves it tickable, so submitting re-subscribes it", () => {
    const tags = html(pages).match(/<input[^>]*>/g) ?? [];
    const tagFor = (pageId: string) =>
      tags.find((tag) => tag.includes(`value="${pageId}"`)) ?? "";

    expect(tagFor("p1")).toContain('checked=""');
    expect(tagFor("p1")).not.toContain('disabled=""');
    expect(tagFor("p2")).not.toContain('checked=""');
  });

  it("keeps the submit enabled when every Page is already connected, so they can be refreshed", () => {
    const markup = html([
      { pageId: "p1", name: "Shop Dhaka", connected: true },
    ]);

    expect(markup).not.toMatch(
      /<button[^>]*disabled=""[^>]*type="submit"|<button[^>]*type="submit"[^>]*disabled=""/,
    );
  });
});
