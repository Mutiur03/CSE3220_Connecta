// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeAll, describe, expect, it } from "vitest";
import type { MemberAvailability } from "@/lib/assignment-api";
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
const action = async () => ({ notice: "Assignee saved." });

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

const mount = (currentId: string | null) => {
  container = document.createElement("div");
  document.body.append(container);
  root = createRoot(container);
  act(() =>
    root.render(
      <AssignConversationForm
        action={action}
        members={members}
        currentId={currentId}
      />,
    ),
  );
};

const select = () => container.querySelector<HTMLSelectElement>("#assignee")!;
const saveButton = () =>
  container.querySelector<HTMLButtonElement>("button[type=submit]")!;

describe("AssignConversationForm", () => {
  it("starts on the saved assignee with Save off", () => {
    mount("m2");
    expect(select().value).toBe("m2");
    expect(saveButton().disabled).toBe(true);
  });

  it("keeps showing the new assignee after a save, which resets the form", async () => {
    mount(null);
    act(() => {
      select().value = "m1";
      select().dispatchEvent(new Event("change", { bubbles: true }));
    });
    expect(saveButton().disabled).toBe(false);

    await act(async () => {
      saveButton().form!.requestSubmit();
    });
    // The server refresh hands the form its new saved assignee.
    await act(async () =>
      root.render(
        <AssignConversationForm
          action={action}
          members={members}
          currentId="m1"
        />,
      ),
    );

    expect(select().value).toBe("m1");
    expect(saveButton().disabled).toBe(true);
  });
});
