// Test doubles for the browser audio APIs the voice recorder uses (jsdom has none of them).
import { vi } from "vitest";

export class FakeMediaRecorder {
  static last: FakeMediaRecorder | undefined;
  state: "inactive" | "recording" = "inactive";
  mimeType = "audio/webm;codecs=opus";
  ondataavailable: ((event: { data: Blob }) => void) | null = null;
  onstop: (() => void) | null = null;

  constructor() {
    FakeMediaRecorder.last = this;
  }
  start() {
    this.state = "recording";
  }
  stop() {
    this.state = "inactive";
    this.ondataavailable?.({ data: new Blob(["webm"]) });
    this.onstop?.();
  }
}

class FakeAudioContext {
  static decode = vi.fn(async () => ({ duration: 2 }));
  decodeAudioData = FakeAudioContext.decode;
  close = vi.fn(async () => {});
}

class FakeOfflineAudioContext {
  destination = {};
  constructor(
    readonly channels: number,
    readonly length: number,
    readonly sampleRate: number,
  ) {}
  createBufferSource() {
    return { buffer: null, connect() {}, start() {} };
  }
  async startRendering() {
    return { getChannelData: () => new Float32Array(this.length) };
  }
}

/** Installs a working microphone; returns its track and getUserMedia so a test can check or break them. */
export function installMicrophone() {
  const track = { stop: vi.fn() };
  const getUserMedia = vi.fn(async () => ({ getTracks: () => [track] }));
  Object.defineProperty(navigator, "mediaDevices", {
    value: { getUserMedia },
    configurable: true,
  });
  FakeAudioContext.decode.mockClear();
  FakeAudioContext.decode.mockImplementation(async () => ({ duration: 2 }));
  vi.stubGlobal("MediaRecorder", FakeMediaRecorder);
  vi.stubGlobal("AudioContext", FakeAudioContext);
  vi.stubGlobal("OfflineAudioContext", FakeOfflineAudioContext);
  return { track, getUserMedia, decode: FakeAudioContext.decode };
}

export function removeMicrophone() {
  Reflect.deleteProperty(navigator, "mediaDevices");
  vi.unstubAllGlobals();
  FakeMediaRecorder.last = undefined;
}

export const micError = (name: string) => new DOMException("denied", name);
