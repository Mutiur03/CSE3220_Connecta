import Link from "next/link";
import { BuildingIcon } from "@/components/icons";
import { buttonClasses } from "@/components/ui/classes";

export default function OrganizationNotFound() {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-4 py-16 text-center">
      <span className="grid size-10 place-items-center rounded-control border border-line bg-surface text-primary">
        <BuildingIcon />
      </span>
      <div className="flex flex-col gap-1">
        <h1 className="text-title font-medium">Organization not found</h1>
        <p className="text-secondary">
          It may have been deleted, or you are not a member of it.
        </p>
      </div>
      <Link href="/organizations" className={buttonClasses("primary")}>
        Your organizations
      </Link>
    </div>
  );
}
