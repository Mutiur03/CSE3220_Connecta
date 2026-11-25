// @vitest-environment jsdom
import { act, useEffect } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ATTACHMENT_MAX_BYTES } from "@/lib/attachment-rules";
import {
  useVoiceRecorder,
  VOICE_BLOCKED_MESSAGE,
  VOICE_BUSY_MESSAGE,
  VOICE_CONVERT_MESSAGE,
  VOICE_MAX_SECONDS,
  VOICE_NO_MIC_MESSAGE,
  VOICE_TOO_SHORT_MESSAGE,
  voiceFileName,
} from "./use-voice-recorder";
import {
  installMicrophone,
  micError,
  removeMicrophone,
} from "./use-voice-recorder.fakes";

(
  globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
let root: Root;
let recorder: ReturnType<typeof useVoiceRecorder>;
const onStarted = vi.fn();
const onRecorded = vi.fn();
const onError = vi.fn();

function Harness() {
  const latest = useVoiceRecorder({ onStarted, onRecorded, onError });
  useEffect(() => {
    recorder = latest;
  });
  return null;
}

const flush = () =>
  act(async () => {
    for (let i = 0; i < 20; i++) await Promise.resolve();
  });
const wait = (ms: number) =>
  act(() => {
    vi.advanceTimersByTime(ms);
  });

beforeEach(() => {
  vi.useFakeTimers({ now: new Date(2026, 10, 25, 14, 30, 5) });
  container = document.createElement("div");
  root = createRoot(container);
});
afterEach(() => {
  act(() => root.unmount());
  removeMicrophone();
  vi.useRealTimers();
  vi.clearAllMocks();
});

const mount = () => act(() => root.render(<Harness />));
const start = async () => {
  await act(async () => {
    await recorder.start();
  });
};

describe("useVoiceRecorder", () => {
  it("is not supported without getUserMedia or MediaRecorder", () => {
    mount();
    expect(recorder.supported).toBe(false);
  });

  it("starts with echo cancellation and noise suppression and counts seconds", async () => {
    const { getUserMedia } = installMicrophone();
    mount();
    expect(recorder.supported).toBe(true);
    await start();
    expect(getUserMedia).toHaveBeenCalledWith({
      audio: { echoCancellation: true, noiseSuppression: true },
    });
    expect(recorder.status).toBe("recording");
    expect(onStarted).toHaveBeenCalled();
    await wait(2000);
    expect(recorder.seconds).toBe(2);
  });

  it("stops into a 16 kHz mono WAV file and releases the microphone", async () => {
    const { track } = installMicrophone();
    mount();
    await start();
    await wait(2000);
    act(() => recorder.stop());
    await flush();
    expect(track.stop).toHaveBeenCalled();
    const [file, capped] = onRecorded.mock.calls[0] as [File, boolean];
    expect(file.type).toBe("audio/wav");
    expect(file.name).toBe("voice-message-20261125-143007.wav");
    expect(file.size).toBe(44 + 2 * 16000 * 2);
    expect(capped).toBe(false);
    expect(recorder.status).toBe("idle");
  });

  it("cancels without a file and releases the microphone", async () => {
    const { track } = installMicrophone();
    mount();
    await start();
    await wait(2000);
    act(() => recorder.cancel());
    await flush();
    expect(track.stop).toHaveBeenCalled();
    expect(onRecorded).not.toHaveBeenCalled();
    expect(recorder.status).toBe("idle");
  });

  it("discards a recording under a second", async () => {
    installMicrophone();
    mount();
    await start();
    await wait(500);
    act(() => recorder.stop());
    await flush();
    expect(onRecorded).not.toHaveBeenCalled();
    expect(onError).toHaveBeenCalledWith(VOICE_TOO_SHORT_MESSAGE);
  });

  it("stops and attaches by itself at the cap, which fits under 8 MB", async () => {
    const { track } = installMicrophone();
    expect(VOICE_MAX_SECONDS).toBe(240);
    expect(44 + VOICE_MAX_SECONDS * 16000 * 2).toBeLessThan(
      ATTACHMENT_MAX_BYTES,
    );
    mount();
    await start();
    await wait(VOICE_MAX_SECONDS * 1000);
    await flush();
    expect(track.stop).toHaveBeenCalled();
    expect(onRecorded.mock.calls[0][1]).toBe(true);
  });

  it.each([
    ["NotAllowedError", VOICE_BLOCKED_MESSAGE],
    ["NotFoundError", VOICE_NO_MIC_MESSAGE],
    ["NotReadableError", VOICE_BUSY_MESSAGE],
  ])("puts %s in plain words", async (name, message) => {
    const { getUserMedia } = installMicrophone();
    getUserMedia.mockRejectedValueOnce(micError(name));
    mount();
    await start();
    expect(onError).toHaveBeenCalledWith(message);
    expect(recorder.status).toBe("idle");
  });

  it("reports a recording that cannot be converted", async () => {
    const { track, decode } = installMicrophone();
    decode.mockRejectedValueOnce(new Error("bad data"));
    mount();
    await start();
    await wait(2000);
    act(() => recorder.stop());
    await flush();
    expect(track.stop).toHaveBeenCalled();
    expect(onError).toHaveBeenCalledWith(VOICE_CONVERT_MESSAGE);
  });

  it("releases the microphone on unmount", async () => {
    const { track } = installMicrophone();
    mount();
    await start();
    act(() => root.render(<></>));
    expect(track.stop).toHaveBeenCalled();
  });

  it("names the file by local date and time", () => {
    expect(voiceFileName(new Date(2026, 0, 2, 3, 4, 5))).toBe(
      "voice-message-20260102-030405.wav",
    );
  });
});
