"use client";

import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import { ATTACHMENT_MAX_BYTES } from "@/lib/attachment-rules";
import { encodeWav, WAV_HEADER_BYTES } from "@/lib/wav";

export const VOICE_SAMPLE_RATE = 16000;
const WAV_BYTES_PER_SECOND = VOICE_SAMPLE_RATE * 2;
/** Four minutes of 16 kHz mono 16-bit WAV is about 7.7 MB, kept under the 8 MB attachment limit. */
export const VOICE_MAX_SECONDS = Math.min(
  4 * 60,
  Math.floor((ATTACHMENT_MAX_BYTES - WAV_HEADER_BYTES) / WAV_BYTES_PER_SECOND),
);
export const VOICE_MIN_MS = 1000;
const TICK_MS = 250;

export const VOICE_TOO_SHORT_MESSAGE = "Recording was too short.";
export const VOICE_BLOCKED_MESSAGE =
  "Microphone access is blocked. Allow it in the browser to record.";
export const VOICE_NO_MIC_MESSAGE = "No microphone was found.";
export const VOICE_BUSY_MESSAGE =
  "The microphone is in use by another app. Close it and try again.";
export const VOICE_CONVERT_MESSAGE =
  "The recording could not be prepared. Try again.";
export const VOICE_UNSUPPORTED_MESSAGE = "This browser cannot record audio.";

const MIC_ERRORS: Record<string, string> = {
  NotAllowedError: VOICE_BLOCKED_MESSAGE,
  SecurityError: VOICE_BLOCKED_MESSAGE,
  NotFoundError: VOICE_NO_MIC_MESSAGE,
  OverconstrainedError: VOICE_NO_MIC_MESSAGE,
  NotReadableError: VOICE_BUSY_MESSAGE,
  AbortError: VOICE_BUSY_MESSAGE,
};

export type VoiceStatus = "idle" | "starting" | "recording" | "saving";

type Options = {
  /** The microphone is on and recording has begun. */
  onStarted: () => void;
  /** The finished WAV file; `capped` is true when the time limit stopped it. */
  onRecorded: (file: File, capped: boolean) => void;
  onError: (message: string) => void;
};

type Session = {
  stream: MediaStream;
  recorder: MediaRecorder;
  recorded: Promise<Blob>;
  startedAt: number;
  clearTimers: () => void;
};

const canRecord = () =>
  typeof navigator.mediaDevices?.getUserMedia === "function" &&
  typeof MediaRecorder === "function";
const noSubscribe = () => () => {};

const pad = (n: number) => String(n).padStart(2, "0");
/** "voice-message-20261125-143005.wav", local time. */
export const voiceFileName = (at: Date) =>
  `voice-message-${at.getFullYear()}${pad(at.getMonth() + 1)}${pad(at.getDate())}-${pad(at.getHours())}${pad(at.getMinutes())}${pad(at.getSeconds())}.wav`;

/** Decodes what MediaRecorder made (WebM/Opus, which Meta refuses), mixes it to 16 kHz mono and encodes WAV. */
async function toWav(recorded: Blob) {
  const context = new AudioContext();
  try {
    const decoded = await context.decodeAudioData(await recorded.arrayBuffer());
    const offline = new OfflineAudioContext(
      1,
      Math.max(1, Math.ceil(decoded.duration * VOICE_SAMPLE_RATE)),
      VOICE_SAMPLE_RATE,
    );
    const source = offline.createBufferSource();
    source.buffer = decoded;
    source.connect(offline.destination);
    source.start();
    const rendered = await offline.startRendering();
    return encodeWav(rendered.getChannelData(0), VOICE_SAMPLE_RATE);
  } finally {
    void context.close();
  }
}

/**
 * Records one voice message: asks for the microphone, runs MediaRecorder, counts the seconds, stops itself
 * at the cap and hands back a WAV file. The microphone is released on stop, cancel, error and unmount.
 * `supported` is false on the server and on the first client render, so both render the same markup.
 */
export function useVoiceRecorder({ onStarted, onRecorded, onError }: Options) {
  const supported = useSyncExternalStore(noSubscribe, canRecord, () => false);
  const [status, setStatus] = useState<VoiceStatus>("idle");
  const [seconds, setSeconds] = useState(0);
  const session = useRef<Session | null>(null);
  // Bumped on cancel and unmount, so a microphone prompt or conversion that finishes late is dropped.
  const generation = useRef(0);
  const callbacks = useRef({ onStarted, onRecorded, onError });
  useEffect(() => {
    callbacks.current = { onStarted, onRecorded, onError };
  });

  const release = () => {
    const current = session.current;
    session.current = null;
    if (!current) return undefined;
    current.clearTimers();
    if (current.recorder.state !== "inactive") current.recorder.stop();
    current.stream.getTracks().forEach((track) => track.stop());
    return current;
  };

  const fail = (run: number, message: string) => {
    if (run !== generation.current) return;
    setStatus("idle");
    callbacks.current.onError(message);
  };

  const stop = async (capped = false) => {
    const current = release();
    if (!current) return;
    const run = generation.current;
    const duration = Date.now() - current.startedAt;
    setStatus("saving");
    const recorded = await current.recorded;
    if (duration < VOICE_MIN_MS) return fail(run, VOICE_TOO_SHORT_MESSAGE);
    let wav: Uint8Array<ArrayBuffer>;
    try {
      wav = await toWav(recorded);
    } catch {
      return fail(run, VOICE_CONVERT_MESSAGE);
    }
    if (run !== generation.current) return;
    setStatus("idle");
    callbacks.current.onRecorded(
      new File([wav], voiceFileName(new Date()), { type: "audio/wav" }),
      capped,
    );
  };

  const record = (stream: MediaStream): Session => {
    const recorder = new MediaRecorder(stream);
    const chunks: Blob[] = [];
    recorder.ondataavailable = (event) => {
      if (event.data.size > 0) chunks.push(event.data);
    };
    const recorded = new Promise<Blob>((resolve) => {
      recorder.onstop = () =>
        resolve(new Blob(chunks, { type: recorder.mimeType }));
    });
    recorder.start();
    const startedAt = Date.now();
    const tick = setInterval(
      () => setSeconds(Math.floor((Date.now() - startedAt) / 1000)),
      TICK_MS,
    );
    const cap = setTimeout(() => void stop(true), VOICE_MAX_SECONDS * 1000);
    return {
      stream,
      recorder,
      recorded,
      startedAt,
      clearTimers: () => {
        clearInterval(tick);
        clearTimeout(cap);
      },
    };
  };

  const start = async () => {
    if (status !== "idle") return;
    const run = ++generation.current;
    setStatus("starting");
    setSeconds(0);
    let stream: MediaStream;
    try {
      stream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true },
      });
    } catch (error) {
      const name = String((error as { name?: string } | null)?.name);
      return fail(run, MIC_ERRORS[name] ?? VOICE_UNSUPPORTED_MESSAGE);
    }
    if (run !== generation.current) {
      stream.getTracks().forEach((track) => track.stop());
      return;
    }
    try {
      session.current = record(stream);
      setStatus("recording");
      callbacks.current.onStarted();
    } catch {
      stream.getTracks().forEach((track) => track.stop());
      fail(run, VOICE_UNSUPPORTED_MESSAGE);
    }
  };

  const cancel = () => {
    generation.current++;
    release();
    setStatus("idle");
    setSeconds(0);
  };

  // Leaving the page or the thread mid-recording must not leave the microphone on.
  useEffect(
    () => () => {
      generation.current++;
      release();
    },
    [],
  );

  return {
    supported,
    status,
    seconds,
    start,
    stop: () => void stop(),
    cancel,
  };
}
