"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";

export const AUTO_REFRESH_MS = 30_000;

/** Re-renders the server page on an interval while the tab is visible. Renders nothing. */
export default function AutoRefresh() {
  const router = useRouter();

  useEffect(() => {
    let lastRefresh = Date.now();
    const refresh = () => {
      // A save in flight marks its control aria-busy; refreshing then would replace it mid-save.
      if (document.querySelector('[aria-busy="true"]')) return;
      lastRefresh = Date.now();
      router.refresh();
    };
    const tick = () => {
      if (document.visibilityState === "visible") refresh();
    };
    const onVisible = () => {
      if (
        document.visibilityState === "visible" &&
        Date.now() - lastRefresh > AUTO_REFRESH_MS
      ) {
        refresh();
      }
    };
    const timer = setInterval(tick, AUTO_REFRESH_MS);
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      clearInterval(timer);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [router]);

  return null;
}
