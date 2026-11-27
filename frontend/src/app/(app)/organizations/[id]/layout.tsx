import type { ReactNode } from "react";
import PresenceConnection from "@/components/presence-connection";
import { isUuid } from "@/lib/org-validation";

/** Every page of an organization keeps the member online while it is open. */
export default async function OrganizationLayout({
  children,
  params,
}: {
  children: ReactNode;
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return (
    <>
      {isUuid(id) && <PresenceConnection orgId={id} />}
      {children}
    </>
  );
}
