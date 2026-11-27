// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest";

const accept = vi.hoisted(() => vi.fn());
vi.mock("@/lib/auth-actions", () => ({ acceptInvitationAsNewUser: accept }));

import AcceptNewForm from "./accept-new-form";
import { mountForm, submit, unmountAll } from "./form-test-harness";

afterEach(() => {
  unmountAll();
  vi.clearAllMocks();
});

describe("AcceptNewForm", () => {
  it("fixes the invited email as read-only and sends the token hidden", async () => {
    accept.mockResolvedValue({});
    const c = mountForm(<AcceptNewForm token="tok-1" email="new@acme.test" />);
    const email = c.querySelector<HTMLInputElement>("#invited-email")!;
    expect(email.readOnly).toBe(true);
    expect(email.value).toBe("new@acme.test");
    await submit(c.querySelector("form")!);
    expect((accept.mock.calls[0][1] as FormData).get("token")).toBe("tok-1");
  });

  it("focuses the name field when both fields fail", async () => {
    accept.mockResolvedValue({ errors: { fullName: "Enter your name.", password: "Too short." } });
    const c = mountForm(<AcceptNewForm token="t" email="n@acme.test" />);
    await submit(c.querySelector("form")!);
    expect(document.activeElement).toBe(c.querySelector("#fullName"));
  });

  it("focuses the password field when only it fails, and announces the message", async () => {
    accept.mockResolvedValue({ errors: { password: "Too short." }, message: "This invitation has expired." });
    const c = mountForm(<AcceptNewForm token="t" email="n@acme.test" />);
    await submit(c.querySelector("form")!);
    expect(document.activeElement).toBe(c.querySelector("#password"));
    expect(c.querySelector('[role="alert"]')?.textContent).toBe("This invitation has expired.");
  });
});
