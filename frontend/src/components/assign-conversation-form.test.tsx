// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import type { MemberAvailability } from "@/lib/assignment-api";
import type { OrgActionState } from "@/lib/org-actions";
import AssignConversationForm from "./assign-conversation-form";

const member = (id: string, name: string): MemberAvailability => ({
  membershipId: id,
  userId: `u-${id}`,
  email: `${id}@acme.test`,
  name,
  role: "AGENT",
  status: "AVAILABLE",
  updatedAt: null,
  openConversations: 0,
});

const members = [member("m1", "Mira Das"), member("m2", "Noor Ali")];

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

const render = (action: Action, currentId: string | null) =>
  act(async () =>
    root.render(
      <AssignConversationForm
        action={action}
        members={members}
        currentId={currentId}
      />,
    ),
  );

const mount = async (action: Action, currentId: string | null) => {
  container = document.createElement("div");
  document.body.append(container);
  root = createRoot(container);
  await render(action, currentId);
};

const select = () => container.querySelector<HTMLSelectElement>("#assignee")!;
const pick = (value: string) =>
  act(async () => {
    select().value = value;
    select().dispatchEvent(new Event("change", { bubbles: true }));
  });

describe("AssignConversationForm", () => {
  it("starts on the saved assignee and has no save button", async () => {
    await mount(async () => ({}), "m2");
    expect(select().value).toBe("m2");
    expect(container.querySelector("button")).toBeNull();
  });

  it("saves as soon as a member is picked and keeps showing them after the refresh", async () => {
    const action = vi.fn<Action>(async () => ({ notice: "Assignee saved." }));
    await mount(action, null);

    await pick("m1");
    expect(action).toHaveBeenCalledOnce();
    expect(action.mock.calls[0][1].get("membershipId")).toBe("m1");
    expect(container.textContent).toContain("Assignee saved.");

    // The server refresh hands the form its new saved assignee.
    await render(action, "m1");
    expect(select().value).toBe("m1");
  });

  it("saves an empty id to unassign", async () => {
    const action = vi.fn<Action>(async () => ({ notice: "Unassigned." }));
    await mount(action, "m1");

    await pick("");
    expect(action.mock.calls[0][1].get("membershipId")).toBe("");
  });

  it("shows the failure and falls back to the saved assignee", async () => {
    const action = vi.fn<Action>(async () => ({
      message: "That member's account is disabled.",
    }));
    await mount(action, "m2");

    await pick("m1");
    expect(container.querySelector('[role="alert"]')?.textContent).toContain(
      "disabled",
    );
    expect(select().value).toBe("m2");
  });
});
