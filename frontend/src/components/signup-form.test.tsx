// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest";
import type { SignupState } from "@/lib/auth-actions";
import { PASSWORD_MIN } from "@/lib/validation";
import SignupForm from "./signup-form";
import { mountForm, submit, typeInto, unmountAll } from "./form-test-harness";

afterEach(unmountAll);

const props = (action: (p: SignupState, f: FormData) => Promise<SignupState>) => (
  <SignupForm action={action} submitLabel="Create account" pendingLabel="Creating…" />
);

describe("SignupForm", () => {
  it("shows the password rule as a hint linked to the field", () => {
    const c = mountForm(props(vi.fn()));
    const password = c.querySelector<HTMLInputElement>("#password")!;
    expect(password.getAttribute("aria-describedby")).toBe("password-hint");
    expect(c.querySelector("#password-hint")?.textContent).toContain(String(PASSWORD_MIN));
    expect(c.querySelector("button[type=submit]")?.textContent).toBe("Create account");
  });

  it("focuses the first invalid field, in form order", async () => {
    const action = vi.fn(async () => ({
      errors: { email: "Enter a valid email.", password: "Too short." },
    }));
    const c = mountForm(props(action));
    await submit(c.querySelector("form")!);
    expect(document.activeElement).toBe(c.querySelector("#email"));
    expect(c.querySelector("#password")?.getAttribute("aria-invalid")).toBe("true");
  });

  it("keeps the typed password and email after a taken-email failure", async () => {
    const action = vi.fn(async () => ({
      message: "That email is already registered.",
      fullName: "Ada",
      email: "ada@acme.test",
    }));
    const c = mountForm(props(action));
    typeInto(c.querySelector("#password")!, "long-enough-pass");
    await submit(c.querySelector("form")!);
    expect(c.querySelector<HTMLInputElement>("#password")!.value).toBe("long-enough-pass");
    expect(c.querySelector<HTMLInputElement>("#email")!.value).toBe("ada@acme.test");
    expect(c.querySelector('[role="alert"]')?.textContent).toContain("already registered");
  });
});
