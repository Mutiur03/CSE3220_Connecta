"use client";

import { useEffect } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";

/**
 * The server cannot know the reader's time zone, so a page whose days depend on it carries the zone in its URL.
 * When the URL has none yet, this adds the browser's zone once; the server then renders the page again for it.
 */
// ponytail: the first visit renders twice (UTC, then the reader's zone); a zone cookie would save that request.
export default function ReaderZone() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const hasZone = searchParams.has("zone");

  useEffect(() => {
    if (hasZone) return;
    const query = new URLSearchParams(searchParams);
    query.set("zone", Intl.DateTimeFormat().resolvedOptions().timeZone);
    router.replace(`${pathname}?${query}`, { scroll: false });
  }, [hasZone, pathname, router, searchParams]);

  return null;
}
