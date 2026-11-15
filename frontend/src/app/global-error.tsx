"use client";

import "./globals.css";
import { Button } from "@/components/ui/button";

// Replaces the root layout, so it brings its own document and the global styles.
export default function GlobalError({
  retry,
}: {
  error: unknown;
  retry: () => void;
}) {
  return (
    <html lang="en" className="h-full antialiased">
      <body className="min-h-full flex flex-col">
        <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 p-6 sm:p-8">
          <div className="flex flex-col gap-2">
            <h1 className="text-display font-semibold tracking-tight">
              Connecta could not load
            </h1>
            <p className="text-ink-muted">
              Something stopped the app from starting. Try again in a moment.
            </p>
          </div>
          <div>
            <Button variant="primary" onClick={retry} className="min-h-11">
              Try again
            </Button>
          </div>
        </main>
      </body>
    </html>
  );
}
