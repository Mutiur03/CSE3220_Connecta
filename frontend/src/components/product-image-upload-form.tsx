"use client";

import { useRef, useState, useTransition } from "react";
import type { OrgActionState } from "@/lib/org-actions";
import {
  fileNamesLabel,
  uploadImageBatch,
  uploadSummary,
  type FileOutcome,
  type UploadResult,
} from "@/lib/product-image-batch";
import {
  IMAGE_LIMIT_MESSAGE,
  IMAGE_MISSING_MESSAGE,
  IMAGE_UPLOAD_FAILED_MESSAGE,
  PRODUCT_IMAGE_LIMIT,
  PRODUCT_IMAGE_TYPES,
  validateProductImage,
} from "@/lib/product-image-validation";
import { Button, PendingLabel } from "./ui/button";

type Props = {
  /** The server action bound to this organization and product: one file per call. */
  action: (previous: OrgActionState, formData: FormData) => Promise<OrgActionState>;
  /** Images the product has now. The limit is checked against it before each upload. */
  existing: number;
};

const ACCEPT = PRODUCT_IMAGE_TYPES.join(",");
const HINT = "JPEG, PNG or WebP, up to 5 MB each. Choose several at once.";

/** Sends one file through the action and turns its state into a result for the list. */
async function sendOne(action: Props["action"], file: File): Promise<UploadResult> {
  const body = new FormData();
  body.append("file", file);
  const state = await action({}, body);
  if (state.notice) return { ok: true };
  return { ok: false, reason: state.errors?.file ?? state.message ?? IMAGE_UPLOAD_FAILED_MESSAGE };
}

/**
 * Picks several images and uploads them one after another, one action call per file, so the
 * server enforces the limit on each. Bad files are refused on the picker before any request;
 * the results list then says, per file, whether it was added and why it was not.
 */
export default function ProductImageUploadForm({ action, existing }: Props) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [files, setFiles] = useState<File[]>([]);
  const [pickProblems, setPickProblems] = useState<string[]>([]);
  const [missing, setMissing] = useState(false);
  const [outcomes, setOutcomes] = useState<FileOutcome[]>();
  const [progress, setProgress] = useState<{ position: number; total: number }>();
  const [pending, startUpload] = useTransition();
  const atLimit = existing >= PRODUCT_IMAGE_LIMIT;

  function onPick(event: React.ChangeEvent<HTMLInputElement>) {
    const picked = Array.from(event.currentTarget.files ?? []);
    setFiles(picked);
    setOutcomes(undefined);
    setMissing(false);
    setPickProblems(
      picked.flatMap((file) => {
        const problem = validateProductImage(file);
        return problem ? [`${file.name}: ${problem}`] : [];
      }),
    );
  }

  function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (files.length === 0) {
      setMissing(true);
      return;
    }
    const batch = files;
    if (inputRef.current) inputRef.current.value = "";
    setFiles([]);
    setPickProblems([]);
    setMissing(false);
    setOutcomes(undefined);
    startUpload(async () => {
      const results = await uploadImageBatch(batch, {
        existing,
        upload: (file) => sendOne(action, file),
        onProgress: (position, total) => setProgress({ position, total }),
      });
      setProgress(undefined);
      setOutcomes(results);
    });
  }

  const errors = missing ? [IMAGE_MISSING_MESSAGE, ...pickProblems] : pickProblems;
  const added = outcomes?.filter((outcome) => outcome.added).length ?? 0;
  const submitLabel = files.length > 1 ? `Upload ${files.length} images` : "Upload image";
  const describedBy = ["product-image-hint", errors.length > 0 ? "product-image-errors" : ""]
    .filter(Boolean)
    .join(" ");

  return (
    <form onSubmit={onSubmit} noValidate className="flex max-w-xl flex-col gap-3">
      <input
        ref={inputRef}
        type="file"
        name="file"
        multiple
        accept={ACCEPT}
        aria-label="Product images"
        onChange={onPick}
        disabled={atLimit || pending}
        hidden
      />
      <div className="flex flex-wrap items-center gap-3">
        <Button
          type="button"
          variant="secondary"
          disabled={atLimit || pending}
          aria-describedby={describedBy}
          onClick={() => inputRef.current?.click()}
        >
          Choose images
        </Button>
        <span className="min-w-0 truncate text-sm text-secondary" title={fileNamesLabel(files)}>
          {fileNamesLabel(files)}
        </span>
      </div>
      <p id="product-image-hint" className="text-caption text-secondary">
        {HINT}
      </p>
      {errors.length > 0 && (
        <ul id="product-image-errors" className="flex flex-col gap-1 text-sm text-danger">
          {errors.map((error) => (
            <li key={error}>{error}</li>
          ))}
        </ul>
      )}
      {atLimit && <p className="text-sm text-secondary">{IMAGE_LIMIT_MESSAGE}</p>}
      <div className="flex flex-wrap items-center gap-2">
        <Button type="submit" variant="primary" disabled={atLimit || pending}>
          <PendingLabel pending={pending} busy="Uploading…" idle={submitLabel} />
        </Button>
      </div>
      <div role="status" className="flex flex-col gap-1 text-sm">
        {progress && (
          <p className="text-secondary tabular-nums">
            {`Uploading ${progress.position} of ${progress.total}…`}
          </p>
        )}
        {outcomes && (
          <>
            <p className={added > 0 ? "text-success" : "text-danger"}>
              {uploadSummary(added, outcomes.length)}
            </p>
            <ul className="flex flex-col gap-1">
              {outcomes.map((outcome, index) => (
                <li
                  key={`${outcome.name}-${index}`}
                  className={outcome.added ? "text-secondary" : "text-danger"}
                >
                  {outcome.name}: {outcome.added ? "added" : outcome.reason}
                </li>
              ))}
            </ul>
          </>
        )}
      </div>
    </form>
  );
}
