// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest";
import type { OrgActionState } from "@/lib/org-actions";
import OrgInviteForm from "./org-invite-form";
import { mountForm, submit, unmountAll } from "./form-test-harness";

afterEach(unmountAll);

type Action = (p: OrgActionState, f: FormData) => Promise<OrgActionState>;
const render = (action: Action) =>
  mountForm(<OrgInviteForm action={action} roleOptions={["ADMIN", "AGENT"]} />);

describe("OrgInviteForm", () => {
  it("offers the granted roles, defaulting to the last (least privileged)", () => {
    const c = render(vi.fn());
    const select = c.querySelector<HTMLSelectElement>("#invite-role")!;
    expect([...select.options].map((o) => o.value)).toEqual(["ADMIN", "AGENT"]);
    expect(select.value).toBe("AGENT");
    expect(select.labels?.[0].textContent).toBe("Role");
  });

  it("shows the email error on the field and focuses it", async () => {
    const c = render(vi.fn(async () => ({ errors: { email: "Enter a valid email." } })));
    await submit(c.querySelector("form")!);
    const email = c.querySelector<HTMLInputElement>("#invite-email")!;
    expect(email.getAttribute("aria-invalid")).toBe("true");
    expect(document.activeElement).toBe(email);
    expect(c.querySelector('[role="alert"]')).toBeNull();
  });

  it("shows a backend failure in the alert and keeps the typed email", async () => {
    const c = render(
      vi.fn(async () => ({
        message: "Already a member.",
        values: { email: "x@acme.test", role: "ADMIN" },
      })),
    );
    await submit(c.querySelector("form")!);
    expect(c.querySelector('[role="alert"]')?.textContent).toBe("Already a member.");
    expect(c.querySelector<HTMLInputElement>("#invite-email")!.value).toBe("x@acme.test");
  });

  it("announces a created invitation politely and shows the token to copy", async () => {
    const c = render(vi.fn(async () => ({ notice: "Invitation created.", token: "tok-abc" })));
    await submit(c.querySelector("form")!);
    expect(c.querySelector('[role="status"]')?.textContent).toBe("Invitation created.");
    expect(c.querySelector<HTMLInputElement>("#invitation-token")!.value).toContain("token=tok-abc");
  });
});
