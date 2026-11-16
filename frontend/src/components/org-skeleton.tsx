// Placeholder shapes that keep the organization layout still while data loads. Static on purpose:
// no pulsing, so a slow page does not look like a moving one.

const BAR = "rounded-control bg-hover";
const ROWS = [0, 1, 2];

function SkeletonRows() {
  return (
    <div className="flex flex-col divide-y divide-line-subtle border-y border-line">
      {ROWS.map((row) => (
        <div key={row} className="flex h-11 items-center">
          <div className={`h-3 w-1/3 ${BAR}`} />
        </div>
      ))}
    </div>
  );
}

/** Stand-in for one section (heading and rows) while its data streams in. */
export function OrgSectionSkeleton({ label }: { label: string }) {
  return (
    <div role="status" className="flex flex-col gap-3">
      <span className="sr-only">Loading {label}</span>
      <div aria-hidden="true" className="flex flex-col gap-3">
        <div className={`h-6 w-32 ${BAR}`} />
        <SkeletonRows />
      </div>
    </div>
  );
}

/** Stand-in for a whole organization page: a title block, then one section. */
export function OrgPageSkeleton({ label }: { label: string }) {
  return (
    <div role="status" className="flex flex-col gap-10">
      <span className="sr-only">Loading {label}</span>
      <div aria-hidden="true" className="flex flex-col gap-10">
        <div className="flex flex-col gap-3">
          <div className={`h-4 w-24 ${BAR}`} />
          <div className={`h-7 w-64 max-w-full ${BAR}`} />
          <div className={`h-4 w-48 max-w-full ${BAR}`} />
        </div>
        <div className="flex flex-col gap-3">
          <div className={`h-6 w-32 ${BAR}`} />
          <SkeletonRows />
        </div>
      </div>
    </div>
  );
}
