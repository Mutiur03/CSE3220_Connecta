// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import ReplyComposer from "./reply-composer";
import {
  installMicrophone,
  micError,
  removeMicrophone,
} from "./use-voice-recorder.fakes";

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

function mount(action: Action, attachAction: Action = async () => ({})) {
  const container = document.createElement("div");
  document.body.appendChild(container);
  const root = createRoot(container);
  mounted.push({ container, root });
  act(() =>
    root.render(<ReplyComposer action={action} attachAction={attachAction} />),
  );
  return {
    box: container.querySelector("textarea") as HTMLTextAreaElement,
    button: container.querySelector(
      'button[type="submit"]',
    ) as HTMLButtonElement,
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
  it("names the recipient in the placeholder, with a plain fallback", () => {
    const container = document.createElement("div");
    document.body.appendChild(container);
    const root = createRoot(container);
    mounted.push({ container, root });
    act(() =>
      root.render(
        <ReplyComposer
          action={async () => ({})}
          attachAction={async () => ({})}
          recipient="Nadia Rahman on Messenger"
        />,
      ),
    );
    expect(container.querySelector("textarea")?.placeholder).toBe(
      "Reply to Nadia Rahman on Messenger",
    );
    expect(mount(async () => ({})).box.placeholder).toBe("Write a reply");
  });

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

  it("sends on Enter and keeps Shift+Enter for a new line", async () => {
    const action = vi.fn().mockResolvedValue({});
    const { box } = mount(action);
    type(box, "hi");
    const press = (shiftKey: boolean) =>
      act(async () => {
        box.dispatchEvent(
          new KeyboardEvent("keydown", {
            key: "Enter",
            shiftKey,
            bubbles: true,
            cancelable: true,
          }),
        );
      });
    await press(true);
    expect(action).not.toHaveBeenCalled();
    await press(false);
    expect(action).toHaveBeenCalledTimes(1);
  });

  it("does not send a blank reply on Enter", async () => {
    const action = vi.fn().mockResolvedValue({});
    const { box } = mount(action);
    type(box, "   ");
    await act(async () => {
      box.dispatchEvent(
        new KeyboardEvent("keydown", {
          key: "Enter",
          bubbles: true,
          cancelable: true,
        }),
      );
    });
    expect(action).not.toHaveBeenCalled();
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

  describe("emoji", () => {
    const pick = (container: HTMLElement, name: string) =>
      act(() =>
        (
          container.querySelector(
            `button[aria-label="${name}"]`,
          ) as HTMLButtonElement
        ).click(),
      );

    it("has a named emoji button left of the key hints", () => {
      const { container } = mount(async () => ({}));
      const opener = container.querySelector(
        'button[aria-label="Insert emoji"]',
      );
      const hint = container.querySelector("#reply-text-hint");
      expect(opener).not.toBeNull();
      expect(
        opener!.compareDocumentPosition(hint!) &
          Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy();
    });

    it("inserts the emoji at the caret, keeps the caret after it and focus in the box", () => {
      const { box, container } = mount(async () => ({}));
      type(box, "Thanks!");
      box.setSelectionRange(6, 6);
      pick(container, "Folded hands");
      expect(box.value).toBe("Thanks🙏!");
      expect(box.selectionStart).toBe(6 + "🙏".length);
      expect(document.activeElement).toBe(box);
    });

    it("replaces a selection and counts toward the limit", () => {
      const { box, container } = mount(async () => ({}));
      type(box, "x".repeat(999));
      box.setSelectionRange(0, 0);
      pick(container, "Thumbs up");
      expect(box.value.startsWith("👍")).toBe(true);
      expect(container.textContent).toContain("Remove 1 characters to send.");
    });
  });

  describe("attachment", () => {
    beforeEach(() => {
      Object.assign(URL, {
        createObjectURL: vi.fn(() => "blob:thumb"),
        revokeObjectURL: vi.fn(),
      });
    });

    const png = () =>
      new File([new Uint8Array(2048)], "receipt.png", { type: "image/png" });
    const pdf = () =>
      new File([new Uint8Array(10)], "invoice.pdf", {
        type: "application/pdf",
      });

    const pick = (container: HTMLElement, file: File) =>
      act(() => {
        const input = container.querySelector(
          'input[type="file"]',
        ) as HTMLInputElement;
        Object.defineProperty(input, "files", {
          value: [file],
          configurable: true,
        });
        input.dispatchEvent(new Event("change", { bubbles: true }));
      });

    const fire = (
      target: Element,
      type: string,
      key: "clipboardData" | "dataTransfer",
      files: File[],
    ) => {
      const event = new Event(type, { bubbles: true, cancelable: true });
      Object.defineProperty(event, key, {
        value: { files, types: files.length ? ["Files"] : [], dropEffect: "" },
      });
      act(() => {
        target.dispatchEvent(event);
      });
      return event;
    };

    const status = (container: HTMLElement) =>
      container.querySelector('[role="status"]')?.textContent;
    const removeButton = (container: HTMLElement) =>
      container.querySelector('button[aria-label^="Remove"]');

    it("has a named paperclip button before the emoji button that opens a picker limited to the allow-list", () => {
      const { container } = mount(async () => ({}));
      const attach = container.querySelector(
        'button[aria-label="Attach a file"]',
      ) as HTMLButtonElement;
      const emoji = container.querySelector(
        'button[aria-label="Insert emoji"]',
      );
      expect(
        attach.compareDocumentPosition(emoji!) &
          Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy();
      const input = container.querySelector(
        'input[type="file"]',
      ) as HTMLInputElement;
      expect(input.accept.split(",")).toContain("application/pdf");
      expect(input.accept.split(",")).toContain("image/jpeg");
      const click = vi.spyOn(input, "click");
      act(() => attach.click());
      expect(click).toHaveBeenCalled();
    });

    it("shows a picked file as a chip above the text, announces it and enables Send", () => {
      const { box, button, container } = mount(async () => ({}));
      pick(container, png());
      const chip = removeButton(container);
      expect(chip?.getAttribute("aria-label")).toBe("Remove receipt.png");
      expect(
        chip!.compareDocumentPosition(box) & Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy();
      expect(container.querySelector("img")?.getAttribute("src")).toBe(
        "blob:thumb",
      );
      expect(status(container)).toBe("Attached receipt.png");
      expect(button.disabled).toBe(false);
    });

    it("replaces the file on a new pick and removes it from the chip", () => {
      const { box, button, container } = mount(async () => ({}));
      pick(container, png());
      pick(container, pdf());
      expect(container.textContent).not.toContain("receipt.png");
      act(() => (removeButton(container) as HTMLButtonElement).click());
      expect(removeButton(container)).toBeNull();
      expect(status(container)).toBe("Removed invoice.pdf");
      expect(document.activeElement).toBe(box);
      expect(button.disabled).toBe(true);
    });

    it("refuses a file of the wrong type or over 8 MB with a plain message", () => {
      const { container } = mount(async () => ({}));
      pick(container, new File(["x"], "notes.txt", { type: "text/plain" }));
      expect(container.querySelector("#reply-text-error")?.textContent).toBe(
        "This file type is not supported.",
      );
      const big = new File(["x"], "clip.mp4", { type: "video/mp4" });
      Object.defineProperty(big, "size", { value: 8 * 1024 * 1024 + 1 });
      pick(container, big);
      expect(container.querySelector("#reply-text-error")?.textContent).toBe(
        "This file is larger than 8 MB.",
      );
      expect(removeButton(container)).toBeNull();
    });

    it("attaches a pasted image and leaves pasted text alone", () => {
      const { box, container } = mount(async () => ({}));
      expect(fire(box, "paste", "clipboardData", []).defaultPrevented).toBe(
        false,
      );
      expect(
        fire(box, "paste", "clipboardData", [png()]).defaultPrevented,
      ).toBe(true);
      expect(status(container)).toBe("Attached receipt.png");
    });

    it("shows a drop state over the frame and attaches the dropped file", () => {
      const { container } = mount(async () => ({}));
      const frame = container.querySelector("fieldset") as HTMLFieldSetElement;
      fire(frame, "dragover", "dataTransfer", [pdf()]);
      expect(frame.hasAttribute("data-dragging")).toBe(true);
      fire(frame, "drop", "dataTransfer", [pdf()]);
      expect(frame.hasAttribute("data-dragging")).toBe(false);
      expect(status(container)).toBe("Attached invoice.pdf");
    });

    it("sends the file first, then the text, and clears both", async () => {
      const calls: string[] = [];
      const attach = vi.fn(async (_: object, data: FormData) => {
        calls.push(`file:${(data.get("file") as File).name}`);
        return {};
      });
      const send = vi.fn(async (_: object, data: FormData) => {
        calls.push(`text:${data.get("text")}`);
        return {};
      });
      const { box, form, container } = mount(send, attach);
      pick(container, png());
      type(box, "Here is the receipt");
      await submit(form);
      expect(calls).toEqual(["file:receipt.png", "text:Here is the receipt"]);
      expect(box.value).toBe("");
      expect(removeButton(container)).toBeNull();
    });

    it("sends a file alone without a text message", async () => {
      const send = vi.fn().mockResolvedValue({});
      const attach = vi.fn().mockResolvedValue({});
      const { form, container } = mount(send, attach);
      pick(container, pdf());
      await submit(form);
      expect(attach).toHaveBeenCalledTimes(1);
      expect(send).not.toHaveBeenCalled();
    });

    it("keeps the file and the text when the file fails, and does not send the text", async () => {
      const send = vi.fn().mockResolvedValue({});
      const attach = vi
        .fn()
        .mockResolvedValue({ message: "The reply window is closed." });
      const { box, form, container } = mount(send, attach);
      pick(container, png());
      type(box, "hi");
      await submit(form);
      expect(send).not.toHaveBeenCalled();
      expect(box.value).toBe("hi");
      expect(removeButton(container)).not.toBeNull();
      expect(container.querySelector('[role="alert"]')?.textContent).toBe(
        "The reply window is closed.",
      );
    });

    it("keeps only the text when the file went out but the text failed", async () => {
      const send = vi.fn().mockResolvedValue({ message: "Try again." });
      const { box, form, container } = mount(send, async () => ({}));
      pick(container, png());
      type(box, "hi");
      await submit(form);
      expect(box.value).toBe("hi");
      expect(removeButton(container)).toBeNull();
      expect(container.querySelector('[role="alert"]')?.textContent).toBe(
        "The file was sent, but the text was not. Try again.",
      );
    });

    it("disables the inputs while sending and gives focus back after", async () => {
      let finish: (state: object) => void = () => {};
      const { box, form, container } = mount(
        async () => ({}),
        () => new Promise((resolve) => (finish = resolve)),
      );
      pick(container, png());
      await submit(form);
      expect(box.matches(":disabled")).toBe(true);
      expect(
        (
          container.querySelector(
            'button[aria-label="Attach a file"]',
          ) as HTMLButtonElement
        ).matches(":disabled"),
      ).toBe(true);
      await act(async () => finish({}));
      expect(box.matches(":disabled")).toBe(false);
      expect(document.activeElement).toBe(box);
    });
  });

  describe("voice message", () => {
    beforeEach(() => {
      vi.useFakeTimers({ now: new Date(2026, 10, 25, 14, 30, 5) });
      Object.assign(URL, {
        createObjectURL: vi.fn(() => "blob:voice"),
        revokeObjectURL: vi.fn(),
      });
    });
    afterEach(() => {
      removeMicrophone();
      vi.useRealTimers();
    });

    const button = (container: HTMLElement, name: string) =>
      container.querySelector(
        `button[aria-label="${name}"]`,
      ) as HTMLButtonElement | null;
    const status = (container: HTMLElement) =>
      container.querySelector('[role="status"]')?.textContent;
    const record = (container: HTMLElement) =>
      act(async () => {
        button(container, "Record a voice message")!.click();
        for (let i = 0; i < 5; i++) await Promise.resolve();
      });
    const stop = async (container: HTMLElement) => {
      act(() => {
        vi.advanceTimersByTime(3000);
      });
      await act(async () => {
        button(container, "Stop and attach recording")!.click();
        for (let i = 0; i < 20; i++) await Promise.resolve();
      });
    };

    it("has no microphone button when the browser cannot record", () => {
      const { container } = mount(async () => ({}));
      expect(button(container, "Record a voice message")).toBeNull();
    });

    it("puts a named microphone button after the paperclip and emoji buttons", () => {
      installMicrophone();
      const { container } = mount(async () => ({}));
      const mic = button(container, "Record a voice message");
      expect(mic?.className).toBe(
        button(container, "Attach a file")?.className,
      );
      expect(
        button(container, "Insert emoji")!.compareDocumentPosition(mic!) &
          Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy();
    });

    it("shows a recording bar with the time, focuses Stop and disables the other controls", async () => {
      installMicrophone();
      const { box, button: send, container } = mount(async () => ({}));
      type(box, "hi");
      await record(container);
      expect(status(container)).toBe("Recording started");
      expect(container.textContent).toContain("0:00");
      act(() => {
        vi.advanceTimersByTime(65_000);
      });
      expect(container.textContent).toContain("1:05");
      expect(document.activeElement).toBe(
        button(container, "Stop and attach recording"),
      );
      expect(box.hidden).toBe(true);
      expect(box.disabled).toBe(true);
      expect(send.disabled).toBe(true);
      expect(button(container, "Attach a file")!.matches(":disabled")).toBe(
        true,
      );
      expect(button(container, "Insert emoji")!.matches(":disabled")).toBe(
        true,
      );
      expect(button(container, "Record a voice message")!.disabled).toBe(true);
    });

    it("cancels on Escape, stops the microphone and gives focus back to the box", async () => {
      const { track } = installMicrophone();
      const { box, container } = mount(async () => ({}));
      await record(container);
      act(() => {
        button(container, "Cancel recording")!.dispatchEvent(
          new KeyboardEvent("keydown", { key: "Escape", bubbles: true }),
        );
      });
      expect(track.stop).toHaveBeenCalled();
      expect(status(container)).toBe("Recording cancelled");
      expect(button(container, "Stop and attach recording")).toBeNull();
      expect(document.activeElement).toBe(box);
      expect(box.disabled).toBe(false);
    });

    it("attaches the recording as a WAV chip with a player and sends it as a file", async () => {
      installMicrophone();
      const attach = vi.fn().mockResolvedValue({});
      const send = vi.fn().mockResolvedValue({});
      const { box, form, container } = mount(send, attach);
      await record(container);
      await stop(container);
      expect(status(container)).toBe("Recording attached");
      expect(document.activeElement).toBe(box);
      expect(container.querySelector("audio")?.getAttribute("src")).toBe(
        "blob:voice",
      );
      vi.useRealTimers();
      await submit(form);
      const sent = attach.mock.calls[0][1].get("file") as File;
      expect(sent.name).toBe("voice-message-20261125-143008.wav");
      expect(sent.type).toBe("audio/wav");
      expect(send).not.toHaveBeenCalled();
    });

    it("shows a blocked microphone under the box", async () => {
      const { getUserMedia } = installMicrophone();
      getUserMedia.mockRejectedValueOnce(micError("NotAllowedError"));
      const { container } = mount(async () => ({}));
      await record(container);
      expect(container.querySelector("#reply-text-error")?.textContent).toBe(
        "Microphone access is blocked. Allow it in the browser to record.",
      );
    });
  });
});
