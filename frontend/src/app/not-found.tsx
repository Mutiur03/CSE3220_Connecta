import type { Metadata } from "next";
import Link from "next/link";
import { buttonClasses } from "@/components/ui/classes";

export const metadata: Metadata = { title: "Page not found" };

export default function NotFound() {
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 p-6 sm:p-8">
      <div className="flex flex-col gap-2">
        <h1 className="text-display font-semibold tracking-tight">
          Page not found
        </h1>
        <p className="text-secondary">
          The page you asked for does not exist or has moved.
        </p>
      </div>
      <div>
        <Link href="/" className={`${buttonClasses("primary")} min-h-11`}>
          Go to home
        </Link>
      </div>
    </main>
  );
}
