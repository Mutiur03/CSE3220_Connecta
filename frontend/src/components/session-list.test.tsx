import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import type { DeviceSession } from "@/lib/session-api";

vi.mock("next/cache", () => ({ revalidatePath: vi.fn() }));
vi.mock("@/lib/session", () => ({ getAccessToken: vi.fn() }));

import SessionList from "./session-list";

const CHROME =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
const FIREFOX =
  "Mozilla/5.0 (X11; Linux x86_64; rv:132.0) Gecko/20100101 Firefox/132.0";

const session = (
  id: string,
  userAgent: string | null,
  current = false,
): DeviceSession => ({
  id,
  userAgent,
  ip: "203.0.113.7",
  createdAt: "2026-11-20T09:00:00Z",
  lastUsedAt: "2026-11-24T08:30:00Z",
  current,
});

describe("SessionList", () => {
  it("marks this device and offers sign out only for the others", () => {
    const html = renderToStaticMarkup(
      <SessionList
        sessions={[session("a", CHROME, true), session("b", FIREFOX)]}
      />,
    );

    expect(html).toContain("Chrome on Windows");
    expect(html).toContain("This device");
    expect(html).toContain("Firefox on Linux");
    expect(html).toContain('aria-label="Sign out Firefox on Linux"');
    expect(html).not.toContain('aria-label="Sign out Chrome on Windows"');
    expect(html).toContain("Sign out other devices");
  });

  it("hides sign out other devices when this is the only one", () => {
    const html = renderToStaticMarkup(
      <SessionList sessions={[session("a", CHROME, true)]} />,
    );

    expect(html).not.toContain("Sign out other devices");
  });

  it("falls back to a plain label for an unknown device", () => {
    const html = renderToStaticMarkup(
      <SessionList sessions={[session("a", null, true)]} />,
    );

    expect(html).toContain("Unknown device");
  });

  it("says what to do when there are no sessions", () => {
    const html = renderToStaticMarkup(<SessionList sessions={[]} />);

    expect(html).toContain("No active sessions");
  });
});
