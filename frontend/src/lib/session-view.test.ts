import { describe, expect, it } from "vitest";
import { deviceLabel } from "./session-view";

const CHROME_WINDOWS =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
const EDGE_WINDOWS = `${CHROME_WINDOWS} Edg/130.0.0.0`;
const FIREFOX_LINUX =
  "Mozilla/5.0 (X11; Linux x86_64; rv:132.0) Gecko/20100101 Firefox/132.0";
const SAFARI_IPHONE =
  "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1";
const CHROME_ANDROID =
  "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36";

describe("deviceLabel", () => {
  it.each([
    [CHROME_WINDOWS, "Chrome on Windows"],
    [EDGE_WINDOWS, "Edge on Windows"],
    [FIREFOX_LINUX, "Firefox on Linux"],
    [SAFARI_IPHONE, "Safari on iOS"],
    [CHROME_ANDROID, "Chrome on Android"],
  ])("reads %#", (agent, label) => {
    expect(deviceLabel(agent)).toBe(label);
  });

  it("names the system alone when the browser is unknown", () => {
    expect(deviceLabel("curl (Windows)")).toBe("Windows");
  });

  it("falls back when there is no user agent", () => {
    expect(deviceLabel(null)).toBe("Unknown device");
    expect(deviceLabel("node")).toBe("Unknown device");
  });
});
