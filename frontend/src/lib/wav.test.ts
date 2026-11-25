import { describe, expect, it } from "vitest";
import { encodeWav } from "./wav";

const text = (bytes: Uint8Array, from: number, to: number) =>
  String.fromCharCode(...bytes.subarray(from, to));
const view = (bytes: Uint8Array) => new DataView(bytes.buffer);

describe("encodeWav", () => {
  it("writes a 44-byte mono 16-bit PCM RIFF/WAVE header", () => {
    const bytes = encodeWav(new Float32Array(3), 16000);
    const v = view(bytes);
    expect(text(bytes, 0, 4)).toBe("RIFF");
    expect(text(bytes, 8, 12)).toBe("WAVE");
    expect(text(bytes, 12, 16)).toBe("fmt ");
    expect(v.getUint32(16, true)).toBe(16);
    expect(v.getUint16(20, true)).toBe(1);
    expect(v.getUint16(22, true)).toBe(1);
    expect(v.getUint32(24, true)).toBe(16000);
    expect(v.getUint32(28, true)).toBe(32000);
    expect(v.getUint16(32, true)).toBe(2);
    expect(v.getUint16(34, true)).toBe(16);
    expect(text(bytes, 36, 40)).toBe("data");
  });

  it("sets the RIFF and data lengths from the sample count", () => {
    const bytes = encodeWav(new Float32Array(5), 8000);
    expect(bytes.length).toBe(44 + 10);
    expect(view(bytes).getUint32(4, true)).toBe(36 + 10);
    expect(view(bytes).getUint32(40, true)).toBe(10);
  });

  it("scales samples to 16-bit little-endian and clamps out-of-range values", () => {
    const bytes = encodeWav(
      new Float32Array([0, 1, -1, 0.5, 2, -3, NaN]),
      16000,
    );
    const at = (i: number) => view(bytes).getInt16(44 + i * 2, true);
    expect([0, 1, 2, 3, 4, 5, 6].map(at)).toEqual([
      0, 32767, -32768, 16383, 32767, -32768, 0,
    ]);
    expect(bytes[44 + 2]).toBe(0xff);
    expect(bytes[44 + 3]).toBe(0x7f);
  });

  it("encodes empty input as a header with no data", () => {
    const bytes = encodeWav(new Float32Array(0), 16000);
    expect(bytes.length).toBe(44);
    expect(view(bytes).getUint32(4, true)).toBe(36);
    expect(view(bytes).getUint32(40, true)).toBe(0);
  });
});
