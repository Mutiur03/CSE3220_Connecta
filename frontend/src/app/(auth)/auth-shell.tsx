import Link from "next/link";
import type { ReactNode } from "react";
import { BrandLogo } from "@/components/brand-mark";
import LoginPreview from "./login/login-preview";

// Shared frame for sign in and sign up. No sidebar: the visitor is signed out. Wide screens
// show the inbox the form opens.
export default function AuthShell({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children: ReactNode;
}) {
  return (
    <div className="grid min-h-dvh lg:grid-cols-[minmax(0,1.15fr)_minmax(0,1fr)]">
      <LoginPreview />
      <main className="flex items-center justify-center bg-surface p-6 sm:p-12">
        <div className="enter flex w-full max-w-sm flex-col gap-8">
          <div className="flex flex-col gap-2">
            <Link
              href="/"
              className="mb-4 self-start rounded-control lg:hidden"
            >
              <BrandLogo className="h-14" />
            </Link>
            <h1 className="text-display font-medium">{title}</h1>
            <p className="text-secondary">{description}</p>
          </div>
          {children}
        </div>
      </main>
    </div>
  );
}

// The line under the form that points to the other page, keeping the visitor's `next` target.
export function AuthSwitch({
  question,
  href,
  action,
}: {
  question: string;
  href: string;
  action: string;
}) {
  return (
    <p className="text-center text-sm text-secondary">
      {question}{" "}
      <a
        href={href}
        className="font-medium text-accent underline-offset-4 hover:underline"
      >
        {action}
      </a>
    </p>
  );
}
