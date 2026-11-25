/** Replaces the selection `start..end` of `value` with `insert`; `caret` is the position just after it. */
export function insertAtCaret(
  value: string,
  start: number,
  end: number,
  insert: string,
) {
  const from = Math.max(0, Math.min(start, end, value.length));
  const to = Math.min(Math.max(start, end), value.length);
  return {
    value: value.slice(0, from) + insert + value.slice(to),
    caret: from + insert.length,
  };
}
