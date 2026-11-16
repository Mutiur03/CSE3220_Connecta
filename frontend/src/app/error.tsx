"use client";

import Link from "next/link";
import { Button } from "@/components/ui/button";
import { buttonClasses } from "@/components/ui/classes";

// The error itself is not shown: production hides its message, and it can carry server details.
export default function AppError({
  retry,
}: {
  error: unknown;
  retry: () => void;
}) {
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 p-6 sm:p-8">
      <div className="flex flex-col gap-2">
        <h1 className="text-display font-semibold tracking-tight">
          Something went wrong
        </h1>
        <p className="text-secondary">
          This page could not load. Try again, or go to home if it keeps
          failing.
        </p>
      </div>
      <div className="flex flex-wrap gap-3">
        <Button variant="primary" onClick={retry} className="min-h-11">
          Try again
        </Button>
        <Link href="/" className={`${buttonClasses("secondary")} min-h-11`}>
          Go to home
        </Link>
      </div>
    </main>
  );
}
