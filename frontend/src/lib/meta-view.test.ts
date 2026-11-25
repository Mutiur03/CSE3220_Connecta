import { describe, expect, it } from "vitest";
import {
  canConnectMeta,
  connectionFlash,
  facebookDetailLine,
  facebookStatus,
  historyImportNotice,
  parsePageIds,
  webhookLine,
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

describe("facebookStatus and facebookDetailLine", () => {
  const formatDate = (iso: string) => `date(${iso})`;
  const now = new Date("2026-11-24T00:00:00Z");
  const grant = (expiresAt: string | null) => ({
    connected: true,
    connectedAt: "2026-11-01T00:00:00Z",
    expiresAt,
  });

  it("is not connected without a connection time", () => {
    const none = { connected: false, connectedAt: null, expiresAt: null };
    expect(facebookStatus(none, now)).toBe("not_connected");
    expect(facebookStatus({ ...none, connected: true }, now)).toBe(
      "not_connected",
    );
    expect(facebookDetailLine(none, now, formatDate)).toContain(
      "Sign in with Facebook",
    );
  });

  it("is connected while access lasts, and says until when", () => {
    const live = grant("2027-01-01T00:00:00Z");
    expect(facebookStatus(live, now)).toBe("connected");
    expect(facebookDetailLine(live, now, formatDate)).toBe(
      "Connected on date(2026-11-01T00:00:00Z). Access lasts until date(2027-01-01T00:00:00Z).",
    );
    expect(facebookStatus(grant(null), now)).toBe("connected");
  });

  it("is expired once the access end has passed, and asks to reconnect", () => {
    const old = grant("2026-11-20T00:00:00Z");
    expect(facebookStatus(old, now)).toBe("expired");
    expect(facebookDetailLine(old, now, formatDate)).toContain("Reconnect");
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

describe("historyImportNotice", () => {
  it("says a missing permission failed the import and when it retries", () => {
    const text = historyImportNotice("PERMISSION");

    expect(text).toContain("Importing past conversations failed");
    expect(text).toContain("permission missing");
    expect(text).toContain("retries");
  });

  it("explains a fetch failure without Meta's own text", () => {
    expect(historyImportNotice("FETCH")).toContain("Meta could not be read");
  });

  it("shows nothing when the import did not fail", () => {
    expect(historyImportNotice(null)).toBeNull();
    expect(historyImportNotice(undefined)).toBeNull();
  });
});

describe("webhookLine", () => {
  const now = new Date("2026-11-26T12:00:00Z");

  it("says when the last webhook arrived, in relative time", () => {
    expect(webhookLine("2026-11-26T11:55:00Z", now)).toBe(
      "Last webhook 5 minutes ago",
    );
  });

  it("says none arrived when the time is null", () => {
    expect(webhookLine(null, now)).toBe("No webhook received yet");
  });
});
