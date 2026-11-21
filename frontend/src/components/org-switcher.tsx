"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import type { Organization } from "@/lib/org-api";
import { CURRENT_ORG_COOKIE, selectCurrentOrganization } from "@/lib/current-org";
import { ROLE_LABELS } from "@/lib/org-permissions";
import { BuildingIcon, CheckIcon, ChevronUpDownIcon, PlusIcon } from "./icons";
import { Button } from "./ui/button";
import OrgCreateForm from "./org-create-form";

const ONE_YEAR = 60 * 60 * 24 * 365;

const MENU_ROW =
  "flex min-h-7 items-center gap-2 rounded-control px-2 py-1 text-sm hover:bg-hover";

const ICON_BUTTON =
  "inline-flex size-8 items-center justify-center rounded-control text-secondary transition-colors duration-150 ease-out hover:bg-hover hover:text-primary motion-reduce:transition-none";

const initials = (name: string) => name.slice(0, 2).toUpperCase();

/**
 * Shows the organization the person is working in and lets them switch. "Current" is the
 * organization in the address when there is one, else the last one used (kept in a cookie),
 * else the first.
 */
export default function OrgSwitcher({
  organizations,
  remembered,
}: {
  organizations: Organization[];
  remembered?: string;
}) {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const createDialog = useRef<HTMLDialogElement>(null);

  const current = selectCurrentOrganization(organizations, pathname, remembered);
  const currentId = current?.id;

  useEffect(() => {
    if (!currentId) return;
    document.cookie = `${CURRENT_ORG_COOKIE}=${currentId}; path=/; max-age=${ONE_YEAR}; samesite=lax`;
  }, [currentId]);

  useEffect(() => {
    if (!open) return;
    const onMouseDown = (event: MouseEvent) => {
      if (!root.current?.contains(event.target as Node)) setOpen(false);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", onMouseDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("mousedown", onMouseDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [open]);

  return (
    <div ref={root} className="relative">
      <button
        type="button"
        aria-haspopup="true"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
        className="flex w-full items-center gap-2.5 rounded-control border border-line px-2 py-1.5 text-left transition-colors duration-150 hover:bg-hover motion-reduce:transition-none max-[799px]:size-10 max-[799px]:w-auto max-[799px]:justify-center max-[799px]:border-0 max-[799px]:p-0 pointer-coarse:max-[799px]:size-11"
      >
        <span
          aria-hidden="true"
          className="inline-flex size-8 shrink-0 items-center justify-center rounded-control bg-hover text-caption font-medium text-secondary"
        >
          {current ? initials(current.name) : "–"}
        </span>
        <span className="flex min-w-0 flex-1 flex-col max-[799px]:sr-only">
          <span className="truncate text-sm font-medium">
            {current ? current.name : "No organization"}
          </span>
          <span className="truncate text-caption text-secondary">
            {current ? ROLE_LABELS[current.callerRole] : "Create or join one"}
          </span>
        </span>
        <span className="max-[799px]:hidden">
          <ChevronUpDownIcon />
        </span>
      </button>

      {open && (
        <div className="enter absolute inset-x-0 top-full z-20 mt-1 flex max-[799px]:inset-x-auto max-[799px]:bottom-full max-[799px]:left-0 max-[799px]:top-auto max-[799px]:mb-2 max-[799px]:mt-0 max-[799px]:w-[min(16rem,calc(100vw-1rem))] max-h-80 flex-col overflow-y-auto rounded-panel border border-line bg-surface p-1 shadow-lg">
          {organizations.map((org) => (
            <Link
              key={org.id}
              href={`/organizations/${org.id}`}
              onClick={() => setOpen(false)}
              aria-current={org.id === currentId ? "true" : undefined}
              className={MENU_ROW}
            >
              <span className="min-w-0 flex-1 truncate">{org.name}</span>
              {org.id === currentId && <CheckIcon />}
            </Link>
          ))}
          {organizations.length > 0 && (
            <hr className="my-0.5 border-line-subtle" />
          )}
          <div className="flex justify-end gap-1">
            <button
              type="button"
              aria-label="Create organization"
              title="Create organization"
              onClick={() => {
                setOpen(false);
                createDialog.current?.showModal();
              }}
              className={ICON_BUTTON}
            >
              <PlusIcon />
            </button>
            <Link
              href="/organizations"
              aria-label="All organizations"
              title="All organizations"
              onClick={() => setOpen(false)}
              className={ICON_BUTTON}
            >
              <BuildingIcon />
            </Link>
          </div>
        </div>
      )}

      <dialog
        ref={createDialog}
        aria-labelledby="create-org-title"
        onClick={(event) => {
          if (event.target === event.currentTarget)
            createDialog.current?.close();
        }}
        className="enter m-auto w-[calc(100%-2rem)] max-w-md rounded-panel border border-line bg-surface p-5 text-primary shadow-lg backdrop:bg-black/40"
      >
        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-1">
            <h2 id="create-org-title" className="text-lg font-medium">
              Create an organization
            </h2>
            <p className="text-sm text-secondary">You become its admin</p>
          </div>
          <OrgCreateForm />
          <Button
            type="button"
            block
            onClick={() => createDialog.current?.close()}
          >
            Cancel
          </Button>
        </div>
      </dialog>
    </div>
  );
}
