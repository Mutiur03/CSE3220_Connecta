// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, describe, expect, it, vi } from "vitest";
import ReplyComposer from "./reply-composer";

type Action = Parameters<typeof ReplyComposer>[0]["action"];
(
  globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

const mounted: { container: HTMLDivElement; root: Root }[] = [];
afterEach(() => {
  mounted.splice(0).forEach(({ container, root }) => {
    act(() => root.unmount());
    container.remove();
  });
});

function mount(action: Action) {
  const container = document.createElement("div");
  document.body.appendChild(container);
  const root = createRoot(container);
  mounted.push({ container, root });
  act(() => root.render(<ReplyComposer action={action} />));
  return {
    box: container.querySelector("textarea") as HTMLTextAreaElement,
    button: container.querySelector("button") as HTMLButtonElement,
    form: container.querySelector("form") as HTMLFormElement,
    container,
  };
}

function type(box: HTMLTextAreaElement, value: string) {
  act(() => {
    const setter = Object.getOwnPropertyDescriptor(
      HTMLTextAreaElement.prototype,
      "value",
    )!.set!;
    setter.call(box, value);
    box.dispatchEvent(new Event("input", { bubbles: true }));
  });
}

const submit = async (form: HTMLFormElement) =>
  act(async () => {
    form.requestSubmit();
  });

describe("ReplyComposer", () => {
  it("has a labelled box and a Send button disabled while blank", () => {
    const { box, button, container } = mount(async () => ({}));
    expect(
      container.querySelector(`label[for="${box.id}"]`)?.textContent,
    ).toContain("Reply");
    expect(button.disabled).toBe(true);
    type(box, "   ");
    expect(button.disabled).toBe(true);
    type(box, "hi");
    expect(button.disabled).toBe(false);
  });

  it("shows the remaining characters only near the limit", () => {
    const { box, container } = mount(async () => ({}));
    expect(container.textContent).not.toContain("characters left");
    type(box, "x".repeat(950));
    expect(container.textContent).toContain("50 characters left");
  });

  it("does not cut a paste over the limit; it says how many to remove and blocks Send", () => {
    const { box, button, container } = mount(async () => ({}));
    type(box, "x".repeat(1003));
    expect(box.value).toHaveLength(1003);
    expect(container.textContent).toContain("Remove 3 characters to send.");
    expect(container.textContent).not.toContain("characters left");
    expect(button.disabled).toBe(true);
    type(box, "x".repeat(1000));
    expect(container.textContent).toContain("0 characters left");
    expect(button.disabled).toBe(false);
  });

  it("disables the button and shows the busy label while sending", async () => {
    let finish: (state: object) => void = () => {};
    const { box, button, form } = mount(
      () => new Promise((resolve) => (finish = resolve)),
    );
    type(box, "hi");
    await submit(form);
    expect(button.disabled).toBe(true);
    expect(button.textContent).toContain("Sending…");
    await act(async () => finish({}));
  });

  it("clears the text after a successful send", async () => {
    const action = vi.fn().mockResolvedValue({});
    const { box, form } = mount(action);
    type(box, "hi");
    await submit(form);
    expect(action).toHaveBeenCalled();
    expect(box.value).toBe("");
  });

  it("shows the failure on the alert line and keeps the text", async () => {
    const { box, form, container } = mount(
      vi.fn().mockResolvedValue({ message: "The reply window is closed." }),
    );
    type(box, "hi");
    await submit(form);
    expect(container.querySelector('[role="alert"]')?.textContent).toBe(
      "The reply window is closed.",
    );
    expect(box.value).toBe("hi");
  });
});
