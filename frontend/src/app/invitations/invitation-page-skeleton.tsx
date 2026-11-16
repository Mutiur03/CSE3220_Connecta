// Placeholders shown while invitation pages load. Static blocks only, no motion.
export function InvitationFormSkeleton() {
  return (
    <div aria-hidden="true" className="flex w-full max-w-md flex-col gap-4">
      <div className="h-4 w-32 rounded-control bg-hover" />
      <div className="h-10 rounded-control bg-hover" />
      <div className="h-10 w-40 rounded-control bg-hover" />
    </div>
  );
}

export function InvitationPageSkeleton() {
  return (
    <main
      aria-busy="true"
      className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 p-6 sm:p-8"
    >
      <span role="status" className="sr-only">
        Loading invitation page
      </span>
      <div aria-hidden="true" className="flex flex-col gap-3">
        <div className="h-8 w-64 rounded-control bg-hover" />
        <div className="h-5 w-full max-w-md rounded-control bg-hover" />
      </div>
      <InvitationFormSkeleton />
    </main>
  );
}
