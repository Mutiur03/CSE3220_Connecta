"use client";

import {
  useActionState,
  useId,
  useRef,
  useState,
  useTransition,
  type FormEvent,
} from "react";
import { createPortal } from "react-dom";
import { formatPrice } from "@/lib/format";
import type { OrgActionState } from "@/lib/org-actions";
import { alertText } from "@/lib/org-form-state";
import type { ShareLookup } from "@/lib/product-share-actions";
import type {
  ShareCandidate,
  ShareProductPreview,
  ShareSearchResult,
} from "@/lib/product-share-api";
import { PackageIcon } from "./icons";
import ProductImagePlaceholder from "./product-image-placeholder";
import { StockStatusBadge } from "./stock-status-badge";
import { Alert } from "./ui/alert";
import { Button, PendingLabel } from "./ui/button";
import { fieldClasses } from "./ui/classes";
import { TextareaField } from "./ui/field";

type Props = {
  search: (query: string) => Promise<ShareLookup<ShareSearchResult>>;
  preview: (productId: string) => Promise<ShareLookup<ShareProductPreview>>;
  send: (
    previous: OrgActionState,
    formData: FormData,
  ) => Promise<OrgActionState>;
  /** Switches the trigger off, e.g. while the composer is sending or recording. */
  disabled?: boolean;
};

const TRIGGER =
  "grid size-8 shrink-0 place-items-center rounded-control text-secondary transition-colors duration-150 ease-out hover:bg-hover hover:text-primary disabled:pointer-events-none disabled:opacity-50 motion-reduce:transition-none pointer-coarse:size-11 [&_svg]:size-4.5";

type Chosen = { candidate: ShareCandidate; preview: ShareProductPreview };

/**
 * The edit-and-send step. It owns the send action state and is remounted (by key) per chosen product and
 * per dialog open, so an old failure never shows on a new attempt.
 */
function SendStep({
  chosen,
  send,
  onBack,
  onSent,
  idPrefix,
}: {
  chosen: Chosen;
  send: Props["send"];
  onBack: () => void;
  onSent: () => void;
  idPrefix: string;
}) {
  const [text, setText] = useState(chosen.preview.text);
  const [state, formAction, sending] = useActionState(
    async (previous: OrgActionState, formData: FormData) => {
      const next = await send(previous, formData);
      if (!next.message && !next.errors) onSent();
      return next;
    },
    {},
  );
  return (
    <form action={formAction} className="flex min-h-0 flex-col gap-3">
      <input type="hidden" name="productId" value={chosen.candidate.id} />
      <div className="flex items-center gap-3">
        {chosen.preview.imageUrl ? (
          // eslint-disable-next-line @next/next/no-img-element -- authenticated same-origin proxy route
          <img
            src={chosen.preview.imageUrl}
            alt={chosen.candidate.name}
            className="size-16 shrink-0 rounded-control border border-line object-cover"
          />
        ) : (
          <ProductImagePlaceholder size="lg" />
        )}
        <p className="min-w-0 text-sm text-secondary">
          <span className="block truncate font-medium text-primary">
            {chosen.candidate.name}
          </span>
          {chosen.preview.imageUrl
            ? "The photo is sent first, then this message."
            : "This product has no photo; only the message is sent."}
        </p>
      </div>
      <TextareaField
        id={`${idPrefix}-text`}
        name="text"
        label="Message"
        rows={7}
        value={text}
        onChange={(event) => setText(event.target.value)}
        error={state.errors?.text}
        disabled={sending}
      />
      {alertText(state) && <Alert tone="danger">{alertText(state)}</Alert>}
      <div className="flex justify-end gap-2">
        <Button type="button" disabled={sending} onClick={onBack}>
          Back
        </Button>
        <Button type="submit" variant="primary" disabled={sending}>
          <PendingLabel pending={sending} busy="Sending…" idle="Send" />
        </Button>
      </div>
    </form>
  );
}

/**
 * The product picker behind the composer's package button. A native modal dialog (focus trap, Escape,
 * focus back to the button) holds two steps: search and pick a product, then edit the composed message
 * and send it. It renders in a portal because the composer is itself a form.
 */
export default function ProductShareDialog({
  search,
  preview,
  send,
  disabled,
}: Props) {
  const id = useId();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const requestId = useRef(0);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<ShareSearchResult>();
  const [chosen, setChosen] = useState<Chosen>();
  const [openCount, setOpenCount] = useState(0);
  const [lookupError, setLookupError] = useState<string>();
  const [busy, startTransition] = useTransition();

  const runSearch = (term: string) => {
    const mine = ++requestId.current;
    setLookupError(undefined);
    startTransition(async () => {
      const found = await search(term);
      if (mine !== requestId.current) return;
      if (found.data) setResults(found.data);
      else setLookupError(found.message);
    });
  };

  const open = () => {
    setOpenCount((count) => count + 1);
    setQuery("");
    setChosen(undefined);
    setResults(undefined);
    runSearch("");
    dialogRef.current?.showModal();
  };

  const choose = (candidate: ShareCandidate) => {
    setLookupError(undefined);
    startTransition(async () => {
      const found = await preview(candidate.id);
      if (!found.data) return setLookupError(found.message);
      setChosen({ candidate, preview: found.data });
    });
  };

  const submitSearch = (event: FormEvent) => {
    event.preventDefault();
    runSearch(query);
  };

  const alert = lookupError;

  const dialog = (
    <dialog
      ref={dialogRef}
      aria-labelledby={`${id}-title`}
      onClick={(event) => {
        if (event.target === event.currentTarget) dialogRef.current?.close();
      }}
      className="enter m-auto max-h-[min(36rem,calc(100dvh-2rem))] w-[calc(100%-2rem)] max-w-lg flex-col gap-3 rounded-panel border border-line bg-surface p-4 text-primary shadow-lg backdrop:bg-black/40 open:flex"
    >
      <h2 id={`${id}-title`} className="text-lg font-medium">
        Share a product
      </h2>

      {chosen ? (
        <SendStep
          key={`${openCount}-${chosen.candidate.id}`}
          chosen={chosen}
          send={send}
          onBack={() => setChosen(undefined)}
          onSent={() => dialogRef.current?.close()}
          idPrefix={id}
        />
      ) : (
        <>
          <form role="search" onSubmit={submitSearch} className="flex gap-2">
            <input
              type="search"
              name="q"
              aria-label="Search products by name or SKU"
              placeholder="Search by name or SKU"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              className={fieldClasses}
            />
            <Button type="submit" disabled={busy}>
              Search
            </Button>
          </form>
          {alert && <Alert tone="danger">{alert}</Alert>}
          <ul
            aria-busy={busy}
            className="-mx-4 min-h-0 flex-1 divide-y divide-line overflow-y-auto border-t border-line"
          >
            {results?.content.map((product) => (
              <li key={product.id}>
                <button
                  type="button"
                  onClick={() => choose(product)}
                  disabled={busy}
                  className="flex w-full items-center gap-3 px-4 py-2 text-left transition-colors duration-150 ease-out hover:bg-hover disabled:opacity-60 motion-reduce:transition-none pointer-coarse:min-h-11"
                >
                  {product.imageUrl ? (
                    // eslint-disable-next-line @next/next/no-img-element -- authenticated same-origin proxy route
                    <img
                      src={product.imageUrl}
                      alt=""
                      loading="lazy"
                      className="size-10 shrink-0 rounded-control border border-line object-cover"
                    />
                  ) : (
                    <ProductImagePlaceholder />
                  )}
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium">
                      {product.name}
                    </span>
                    <span className="figure block truncate text-caption text-secondary">
                      {product.sku ?? "No SKU"} ·{" "}
                      {formatPrice(product.unitPrice)}
                    </span>
                  </span>
                  <StockStatusBadge status={product.stockStatus} />
                </button>
              </li>
            ))}
            {results && results.content.length === 0 && (
              <li className="px-4 py-6 text-center text-sm text-secondary">
                No products match.
              </li>
            )}
          </ul>
          {results && results.totalElements > results.content.length && (
            <p className="text-caption text-secondary">
              Showing {results.content.length} of {results.totalElements}.
              Narrow the search to find the rest.
            </p>
          )}
          <Button
            type="button"
            className="self-end"
            onClick={() => dialogRef.current?.close()}
          >
            Close
          </Button>
        </>
      )}
    </dialog>
  );

  return (
    <>
      <button
        type="button"
        onClick={open}
        disabled={disabled}
        aria-label="Share a product"
        title="Share a product"
        aria-haspopup="dialog"
        className={TRIGGER}
      >
        <PackageIcon />
      </button>
      {typeof document === "undefined"
        ? null
        : createPortal(dialog, document.body)}
    </>
  );
}
