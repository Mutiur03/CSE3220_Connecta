// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import type { OrgActionState } from "@/lib/org-actions";
import AssignableSwitch from "./assignable-switch";

type Action = (
  previous: OrgActionState,
  formData: FormData,
) => Promise<OrgActionState>;

let container: HTMLDivElement;
let root: Root;

beforeAll(() => {
  (
    globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }
  ).IS_REACT_ACT_ENVIRONMENT = true;
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

const mount = async (action: Action, assignable: boolean) => {
  container = document.createElement("div");
  document.body.append(container);
  root = createRoot(container);
  await act(async () =>
    root.render(
      <AssignableSwitch action={action} assignable={assignable} name="Mira" />,
    ),
  );
};

const box = () => container.querySelector<HTMLInputElement>("input")!;
const toggle = () => act(async () => box().click());

describe("AssignableSwitch", () => {
  it("is a named switch that starts on the saved value", async () => {
    await mount(async () => ({}), true);
    expect(box().getAttribute("role")).toBe("switch");
    expect(box().getAttribute("aria-label")).toContain("Mira");
    expect(box().checked).toBe(true);
  });

  it("submits the new value as soon as it is flipped", async () => {
    const action = vi.fn<Action>(async () => ({ notice: "Assignable." }));
    await mount(action, false);
    await toggle();
    expect(action).toHaveBeenCalledTimes(1);
    expect(action.mock.calls[0][1].get("assignable")).toBe("on");
    expect(container.querySelector("[role=status]")!.textContent).toBe(
      "Assignable.",
    );
  });

  it("sends nothing checked when switched off", async () => {
    const action = vi.fn<Action>(async () => ({}));
    await mount(action, true);
    await toggle();
    expect(action.mock.calls[0][1].get("assignable")).toBeNull();
  });

  it("shows a failure in an alert and falls back to the saved value", async () => {
    await mount(async () => ({ message: "Could not save." }), false);
    await toggle();
    expect(container.querySelector("[role=alert]")!.textContent).toBe(
      "Could not save.",
    );
    expect(box().checked).toBe(false);
  });
});
