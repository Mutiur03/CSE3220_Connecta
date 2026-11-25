"use client";

import { useState } from "react";
import type { Attachment } from "@/lib/inbox-api";

type Props = {
  attachment: Attachment;
  /** Text alternative for an image. */
  alt: string;
};

// A fixed box, so the thread does not jump when the media loads; the media is contained inside it.
const BOX = "h-60 w-70 max-w-full rounded-[6px] bg-hover object-contain";

/**
 * A photo or video inside a bubble. Meta media links are signed and expire, so a link that fails to load
 * shows a short notice instead of a broken icon. Plain elements, not next/image: the optimizer would cache a
 * link that later dies.
 */
export default function MessageMedia({ attachment, alt }: Props) {
  const [failed, setFailed] = useState(false);
  if (failed) {
    return (
      <span className="rounded-[6px] bg-hover px-3 py-2 text-caption text-secondary">
        Media no longer available
      </span>
    );
  }
  if (attachment.kind === "VIDEO") {
    return (
      <video
        src={attachment.url}
        controls
        preload="metadata"
        className={BOX}
        onError={() => setFailed(true)}
      />
    );
  }
  return (
    <a
      href={attachment.url}
      target="_blank"
      rel="noopener noreferrer"
      className="block w-fit max-w-full rounded-[6px]"
    >
      {/* eslint-disable-next-line @next/next/no-img-element -- signed, expiring Meta URL: must not go through the image optimizer cache */}
      <img
        src={attachment.url}
        alt={alt}
        loading="lazy"
        decoding="async"
        referrerPolicy="no-referrer"
        className={BOX}
        onError={() => setFailed(true)}
      />
      <span className="sr-only"> (opens in a new tab)</span>
    </a>
  );
}
