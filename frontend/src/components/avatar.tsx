"use client";

import { useState } from "react";
import { avatarInitials, avatarTone } from "@/lib/inbox-view";

type Props = {
  /** The person's display name: only used for the initials and the neutral tone, never shown as text. */
  name: string | null;
  /** The Meta picture link; null when none is stored. */
  src: string | null;
  size?: "sm" | "md";
};

const SIZES = {
  sm: "size-9 text-caption",
  md: "size-12 text-sm",
} as const;

const PIXELS = { sm: 36, md: 48 } as const;

/**
 * Round customer avatar. The name always sits next to it, so the image is decorative (`alt=""`) and the circle
 * is hidden from assistive tech. A missing or dead picture shows the initials. Meta picture links are signed and
 * short-lived, so this is a plain `<img>` (next/image would cache a link that later dies) with no referrer sent.
 */
export default function Avatar({ name, src, size = "sm" }: Props) {
  // Remember which link failed, so a refreshed link for the same person is tried again.
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  const showImage = src !== null && src !== failedSrc;
  const pixels = PIXELS[size];

  return (
    <span
      aria-hidden="true"
      className={`inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full font-medium text-secondary ${SIZES[size]} ${avatarTone(name)}`}
    >
      {showImage ? (
        // eslint-disable-next-line @next/next/no-img-element -- signed, expiring Meta URL: must not go through the image optimizer cache
        <img
          src={src}
          alt=""
          width={pixels}
          height={pixels}
          loading="lazy"
          referrerPolicy="no-referrer"
          className="size-full object-cover"
          onError={() => setFailedSrc(src)}
        />
      ) : (
        avatarInitials(name)
      )}
    </span>
  );
}
