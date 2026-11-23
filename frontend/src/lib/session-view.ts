// Browsers first: Edge and Opera also say "Chrome", and Chrome also says "Safari".
const BROWSERS: [RegExp, string][] = [
  [/Edg(?:e|A|iOS)?\//, "Edge"],
  [/OPR\/|Opera/, "Opera"],
  [/Firefox\/|FxiOS\//, "Firefox"],
  [/Chrome\/|CriOS\//, "Chrome"],
  [/Safari\//, "Safari"],
];

// iOS before macOS (iPhone agents say "like Mac OS X"), Android before Linux.
const SYSTEMS: [RegExp, string][] = [
  [/iPhone|iPad|iPod/, "iOS"],
  [/Android/, "Android"],
  [/Windows/, "Windows"],
  [/Mac OS X|Macintosh/, "macOS"],
  [/CrOS/, "ChromeOS"],
  [/Linux/, "Linux"],
];

const UNKNOWN_DEVICE = "Unknown device";

const firstMatch = (table: [RegExp, string][], agent: string) =>
  table.find(([pattern]) => pattern.test(agent))?.[1];

/** "Chrome on Windows" from a User-Agent. Falls back to what it can tell, then to "Unknown device". */
export function deviceLabel(userAgent: string | null): string {
  if (!userAgent) return UNKNOWN_DEVICE;
  const browser = firstMatch(BROWSERS, userAgent);
  const system = firstMatch(SYSTEMS, userAgent);
  if (browser && system) return `${browser} on ${system}`;
  return browser ?? system ?? UNKNOWN_DEVICE;
}
