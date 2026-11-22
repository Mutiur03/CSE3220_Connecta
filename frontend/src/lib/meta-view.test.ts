import { describe, expect, it } from "vitest";
import {
  canConnectMeta,
  connectionFlash,
  facebookStatusLine,
  parsePageIds,
} from "./meta-view";

describe("canConnectMeta", () => {
  it("lets an admin connect and an agent not", () => {
    expect(canConnectMeta("ADMIN")).toBe(true);
    expect(canConnectMeta("AGENT")).toBe(false);
  });
});

describe("parsePageIds", () => {
  it("trims, drops blanks and repeats, and keeps the chosen order", () => {
    const values = ["  p2 ", "", "p1", "p2", "   "];

    expect(parsePageIds(values)).toEqual(["p2", "p1"]);
  });

  it("returns nothing when no Page was chosen", () => {
    expect(parsePageIds([])).toEqual([]);
  });
});

describe("facebookStatusLine", () => {
  const formatDate = (iso: string) => `date(${iso})`;

  it("says not connected without a connection time", () => {
    expect(facebookStatusLine(false, null, formatDate)).toBe("Not connected");
    expect(facebookStatusLine(true, null, formatDate)).toBe("Not connected");
  });

  it("names the date it was connected", () => {
    expect(facebookStatusLine(true, "2026-11-23T00:00:00Z", formatDate)).toBe(
      "Connected on date(2026-11-23T00:00:00Z)",
    );
  });
});

describe("connectionFlash", () => {
  it("confirms a successful Facebook or Instagram connection", () => {
    expect(connectionFlash({ facebook: "connected" })?.tone).toBe("success");
    expect(connectionFlash({ instagram: "connected" })?.text).toBe(
      "Instagram account connected.",
    );
  });

  it("explains each failure in plain words", () => {
    expect(connectionFlash({ metaError: "denied" })?.tone).toBe("danger");
    expect(connectionFlash({ metaError: "invalid_state" })?.text).toContain(
      "expired",
    );
    expect(connectionFlash({ metaError: "forbidden" })?.text).toContain(
      "admin",
    );
  });

  it("shows nothing for a normal visit or an unknown code", () => {
    expect(connectionFlash({})).toBeNull();
    expect(connectionFlash({ metaError: "something_else" })).toBeNull();
  });
});
