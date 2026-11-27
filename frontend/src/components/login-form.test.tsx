// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest";
import type { LoginState } from "@/lib/auth-actions";

const login = vi.hoisted(() => vi.fn());
vi.mock("@/lib/auth-actions", () => ({ login }));

import LoginForm from "./login-form";
import { mountForm, submit, typeInto, unmountAll } from "./form-test-harness";

afterEach(() => {
  unmountAll();
  vi.clearAllMocks();
});

const form = (c: HTMLElement) => c.querySelector("form")!;

describe("LoginForm", () => {
  it("labels both fields and carries the redirect target as a hidden field", () => {
    const c = mountForm(<LoginForm next="/organizations" />);
    expect(c.querySelector<HTMLInputElement>("#email")?.labels?.[0].textContent).toBe("Email");
    expect(c.querySelector<HTMLInputElement>("#password")?.labels?.[0].textContent).toBe("Password");
    expect(c.querySelector<HTMLInputElement>('input[name="next"]')?.value).toBe("/organizations");
  });

  it("submits what was typed to the login action", async () => {
    login.mockResolvedValue({});
    const c = mountForm(<LoginForm />);
    typeInto(c.querySelector("#email")!, "ada@acme.test");
    typeInto(c.querySelector("#password")!, "secret-pass");
    await submit(form(c));
    const data = login.mock.calls[0][1] as FormData;
    expect(data.get("email")).toBe("ada@acme.test");
    expect(data.get("password")).toBe("secret-pass");
  });

  it("marks a field error invalid, links it, and moves focus to the field", async () => {
    login.mockResolvedValue({ errors: { email: "Enter your email." } } satisfies LoginState);
    const c = mountForm(<LoginForm />);
    await submit(form(c));
    const email = c.querySelector<HTMLInputElement>("#email")!;
    expect(email.getAttribute("aria-invalid")).toBe("true");
    expect(c.querySelector(`#${email.getAttribute("aria-describedby")}`)?.textContent).toBe("Enter your email.");
    expect(document.activeElement).toBe(email);
  });

  it("announces a wrong password in an alert and focuses the password field", async () => {
    login.mockResolvedValue({ message: "Invalid email or password." } satisfies LoginState);
    const c = mountForm(<LoginForm />);
    await submit(form(c));
    expect(c.querySelector('[role="alert"]')?.textContent).toBe("Invalid email or password.");
    expect(document.activeElement).toBe(c.querySelector("#password"));
  });

  it("is not busy and shows no alert before a submit", () => {
    const c = mountForm(<LoginForm />);
    expect(form(c).hasAttribute("aria-busy")).toBe(false);
    expect(c.querySelector('[role="alert"]')).toBeNull();
  });
});
