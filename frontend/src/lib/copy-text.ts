type ClipboardWriter = { writeText(text: string): Promise<void> };

/** Copies text and reports whether it worked. A missing or denied clipboard returns false. */
export async function copyText(
  text: string,
  clipboard: ClipboardWriter | undefined = globalThis.navigator?.clipboard,
): Promise<boolean> {
  if (!clipboard) return false;
  try {
    await clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}
