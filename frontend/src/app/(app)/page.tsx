import type { Metadata } from "next";
import { PageHeader } from "@/components/ui/page-header";
import UserPanel from "@/components/user-panel";

export const metadata: Metadata = { title: { absolute: "Home · Connecta" } };

export default function Home() {
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col gap-8 p-6 sm:p-8">
      <PageHeader title="Home" description="Your account at a glance" />
      <UserPanel />
    </main>
  );
}
