// @vitest-environment jsdom
import { act } from "react";
import { createRoot } from "react-dom/client";
import { describe, expect, it } from "vitest";
import type { Attachment } from "@/lib/inbox-api";
import MessageMedia from "./message-media";

(
  globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

const attachment = (kind: Attachment["kind"]): Attachment => ({
  id: "a",
  kind,
  url: "https://cdn.example/expired",
  fileName: null,
  contentType: null,
});

function mount(kind: Attachment["kind"]) {
  const container = document.createElement("div");
  act(() =>
    createRoot(container).render(
      <MessageMedia attachment={attachment(kind)} alt="Photo from Nadia" />,
    ),
  );
  return container;
}

describe("MessageMedia", () => {
  it.each(["img", "video"] as const)(
    "replaces a %s whose link expired with a short notice",
    (tag) => {
      const container = mount(tag === "img" ? "IMAGE" : "VIDEO");
      act(() => {
        container.querySelector(tag)!.dispatchEvent(new Event("error"));
      });
      expect(container.querySelector(tag)).toBeNull();
      expect(container.textContent).toBe("Media no longer available");
    },
  );
});
