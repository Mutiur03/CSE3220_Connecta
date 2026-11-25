"use client";

import { useCallback } from "react";
import { formatFileSize, isAudio, isImage } from "@/lib/attachment-rules";
import { CloseIcon, FileIcon, MicrophoneIcon } from "./icons";

type Props = {
  file: File;
  onRemove: () => void;
};

/**
 * The file waiting in the composer: an image thumbnail (a blob URL, revoked when the chip goes away or the
 * file changes) or a file icon, the name cut to one line, its size, and a remove button. An audio file also
 * gets a native player below, so a voice message can be heard before it is sent.
 */
export default function AttachmentChip({ file, onRemove }: Props) {
  // A ref callback with cleanup: the URL lives exactly as long as this <img> or <audio> shows this file.
  const showFile = useCallback(
    (media: HTMLImageElement | HTMLAudioElement | null) => {
      if (!media) return;
      const url = URL.createObjectURL(file);
      media.src = url;
      return () => URL.revokeObjectURL(url);
    },
    [file],
  );
  const audio = isAudio(file);

  return (
    <div className="flex max-w-full min-w-0 flex-col gap-1 self-start rounded-control border border-line bg-app p-1">
      <div className="flex min-w-0 items-center gap-2">
        {isImage(file) ? (
          // eslint-disable-next-line @next/next/no-img-element -- a local blob URL, not something the optimizer can fetch
          <img
            ref={showFile}
            alt=""
            className="size-8 shrink-0 rounded-[4px] object-cover"
          />
        ) : (
          <span className="grid size-8 shrink-0 place-items-center rounded-[4px] bg-hover text-secondary [&_svg]:size-4">
            {audio ? <MicrophoneIcon /> : <FileIcon />}
          </span>
        )}
        <span className="flex min-w-0 flex-col">
          <span className="truncate text-sm text-primary" title={file.name}>
            {file.name}
          </span>
          <span className="figure text-caption text-secondary">
            {formatFileSize(file.size)}
          </span>
        </span>
        <button
          type="button"
          onClick={onRemove}
          aria-label={`Remove ${file.name}`}
          title="Remove"
          className="grid size-7 shrink-0 place-items-center rounded-control text-secondary transition-colors duration-150 ease-out hover:bg-hover hover:text-primary disabled:opacity-50 motion-reduce:transition-none pointer-coarse:size-11 [&_svg]:size-3.5"
        >
          <CloseIcon />
        </button>
      </div>
      {audio && (
        <audio
          ref={showFile}
          controls
          preload="metadata"
          aria-label={`Listen to ${file.name}`}
          className="h-8 w-72 max-w-full pointer-coarse:h-11"
        />
      )}
    </div>
  );
}
