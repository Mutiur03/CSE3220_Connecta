export const WAV_HEADER_BYTES = 44;
const BYTES_PER_SAMPLE = 2;
const PCM_FORMAT = 1;
const MONO = 1;
const INT16_MAX = 0x7fff;
const INT16_MIN = 0x8000;

/**
 * Mono 16-bit PCM samples (-1..1, clamped) in a 44-byte RIFF/WAVE header, all little-endian. Meta takes
 * WAV but not the WebM/Opus that browsers record, so a voice message is sent in this form.
 */
export function encodeWav(
  samples: Float32Array,
  sampleRate: number,
): Uint8Array<ArrayBuffer> {
  const dataBytes = samples.length * BYTES_PER_SAMPLE;
  const bytes = new Uint8Array(WAV_HEADER_BYTES + dataBytes);
  const view = new DataView(bytes.buffer);
  const ascii = (offset: number, text: string) => {
    for (let i = 0; i < text.length; i++)
      view.setUint8(offset + i, text.charCodeAt(i));
  };
  ascii(0, "RIFF");
  view.setUint32(4, WAV_HEADER_BYTES - 8 + dataBytes, true);
  ascii(8, "WAVE");
  ascii(12, "fmt ");
  view.setUint32(16, 16, true);
  view.setUint16(20, PCM_FORMAT, true);
  view.setUint16(22, MONO, true);
  view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * BYTES_PER_SAMPLE * MONO, true);
  view.setUint16(32, BYTES_PER_SAMPLE * MONO, true);
  view.setUint16(34, BYTES_PER_SAMPLE * 8, true);
  ascii(36, "data");
  view.setUint32(40, dataBytes, true);
  samples.forEach((sample, i) => {
    const clamped = Math.max(-1, Math.min(1, sample || 0));
    view.setInt16(
      WAV_HEADER_BYTES + i * BYTES_PER_SAMPLE,
      clamped < 0 ? clamped * INT16_MIN : clamped * INT16_MAX,
      true,
    );
  });
  return bytes;
}
