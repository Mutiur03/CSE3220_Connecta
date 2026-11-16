import Link from "next/link";
import { buttonClasses } from "@/components/ui/classes";

export default function OrganizationNotFound() {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-4 py-16 text-center">
      <h1 className="text-title font-semibold">Organization not found</h1>
      <p className="text-secondary">
        It may have been deleted, or you are not a member of it.
      </p>
      <Link href="/organizations" className={buttonClasses("primary")}>
        Your organizations
      </Link>
    </div>
  );
}
