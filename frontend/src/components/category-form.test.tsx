// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from "vitest";
import type { OrgActionState } from "@/lib/org-actions";
import CategoryForm from "./category-form";
import { mountForm, submit, unmountAll } from "./form-test-harness";

afterEach(unmountAll);

const render = (
  action: (p: OrgActionState, f: FormData) => Promise<OrgActionState> = vi.fn(async () => ({})),
) =>
  mountForm(
    <CategoryForm
      action={action}
      defaultName="Beverages"
      defaultDescription="Drinks"
      submitLabel="Save"
      busyLabel="Saving…"
      cancelHref="/organizations/o/categories"
    />,
  );

describe("CategoryForm", () => {
  it("prefills the defaults and links cancel to the list", () => {
    const c = render();
    expect(c.querySelector<HTMLInputElement>("#category-name")!.value).toBe("Beverages");
    expect(c.querySelector("a")?.getAttribute("href")).toBe("/organizations/o/categories");
    expect(c.querySelector("button[type=submit]")?.textContent).toBe("Save");
  });

  it("shows a duplicate name on the name field only and focuses it", async () => {
    const c = render(
      vi.fn(async () => ({ errors: { name: "A category with this name already exists." } })),
    );
    await submit(c.querySelector("form")!);
    const name = c.querySelector<HTMLInputElement>("#category-name")!;
    expect(name.getAttribute("aria-invalid")).toBe("true");
    expect(document.activeElement).toBe(name);
    expect(c.querySelector('[role="alert"]')).toBeNull();
  });

  it("shows an action failure in the alert and keeps what was typed", async () => {
    const c = render(
      vi.fn(async () => ({ message: "Something went wrong.", values: { name: "Gifts", description: "" } })),
    );
    await submit(c.querySelector("form")!);
    expect(c.querySelector('[role="alert"]')?.textContent).toBe("Something went wrong.");
    expect(c.querySelector<HTMLInputElement>("#category-name")!.value).toBe("Gifts");
  });
});
