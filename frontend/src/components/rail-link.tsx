"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { RAIL_CONTROL, RAIL_LABEL } from "./rail-button";

const ACTIVE =
  "bg-selected text-primary before:absolute before:-left-3 before:top-2 before:h-5 before:w-0.5 before:rounded-full before:bg-accent";

type RailLinkProps = {
  href: string;
  label: string;
  /** Match only this exact path (Home), not everything below it. */
  exact?: boolean;
  children: ReactNode;
};

export default function RailLink({
  href,
  label,
  exact = false,
  children,
}: RailLinkProps) {
  const pathname = usePathname();
  const active = exact ? pathname === href : pathname.startsWith(href);

  return (
    <Link
      href={href}
      title={label}
      aria-current={active ? "page" : undefined}
      className={`${RAIL_CONTROL} ${active ? ACTIVE : ""}`}
    >
      {children}
      <span className={RAIL_LABEL}>{label}</span>
    </Link>
  );
}
