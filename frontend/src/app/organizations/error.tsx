"use client";

import { Button } from "@/components/ui/button";

export default function OrganizationsError({ reset }: { reset: () => void }) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-4 py-16 text-center">
      <h1 className="text-title font-semibold">This page did not load</h1>
      <p className="text-ink-muted">Check your connection, then try again.</p>
      <Button variant="primary" onClick={reset}>
        Try again
      </Button>
    </div>
  );
}
