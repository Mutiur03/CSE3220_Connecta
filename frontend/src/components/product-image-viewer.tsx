"use client";

import Image from "next/image";
import { useEffect, useRef, type KeyboardEvent } from "react";
import { buttonClasses } from "./ui/classes";

export type ViewerImage = { id: string; src: string };

/** Moves one image forward or back and wraps: after the last image comes the first. */
export function stepIndex(index: number, step: 1 | -1, count: number) {
  return (index + step + count) % count;
}

type Props = {
  productName: string;
  images: ViewerImage[];
  /** The open image's index, or null while the viewer is closed. */
  index: number | null;
  onIndexChange: (index: number) => void;
  onClose: () => void;
};

/**
 * A full-size viewer in a native modal dialog. The browser handles Escape, the focus trap and
 * returning focus to the thumbnail that opened it. Arrow keys move between images.
 */
export default function ProductImageViewer({
  productName,
  images,
  index,
  onIndexChange,
  onClose,
}: Props) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const open = index !== null;

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  const count = images.length;
  const current = index === null ? undefined : images[index];
  const position = (index ?? 0) + 1;
  const move = (step: 1 | -1) => {
    if (index !== null) onIndexChange(stepIndex(index, step, count));
  };

  const onKeyDown = (event: KeyboardEvent<HTMLDialogElement>) => {
    if (event.key === "ArrowRight" || event.key === "ArrowLeft") {
      event.preventDefault();
      move(event.key === "ArrowRight" ? 1 : -1);
    }
  };

  return (
    <dialog
      ref={dialogRef}
      aria-label={`${productName} images`}
      onClose={onClose}
      onKeyDown={onKeyDown}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-5xl flex-col gap-4 rounded-panel border border-line bg-surface p-4 text-primary backdrop:bg-black/40 open:flex"
    >
      {current && (
        <>
          <div className="flex items-center justify-between gap-4">
            <p className="text-sm text-secondary tabular-nums">
              Image {position} of {count}
            </p>
            <form method="dialog">
              <button type="submit" className={buttonClasses("secondary")}>
                Close
              </button>
            </form>
          </div>
          <div className="relative h-[calc(100dvh-11rem)] min-h-48 w-full">
            <Image
              key={current.id}
              src={current.src}
              alt={`${productName}, image ${position}`}
              fill
              unoptimized
              sizes="(min-width: 1024px) 60rem, 100vw"
              className="object-contain"
            />
          </div>
          {count > 1 && (
            <div className="flex items-center justify-between gap-2">
              <button
                type="button"
                onClick={() => move(-1)}
                className={buttonClasses("secondary")}
              >
                Previous image
              </button>
              <button
                type="button"
                onClick={() => move(1)}
                className={buttonClasses("secondary")}
              >
                Next image
              </button>
            </div>
          )}
        </>
      )}
    </dialog>
  );
}
