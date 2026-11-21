import type { StockMovement } from "@/lib/stock-api";
import { formatDateTime } from "@/lib/format";
import { STOCK_MOVEMENT_LABELS } from "@/lib/stock-validation";
import { tableClass } from "./org-styles";
import { TableScroll } from "./ui/table-scroll";

const DASH = "—";

/** A signed change for the history: "+5", "−3", or "0" for an adjustment that changed nothing. */
export function formatDelta(delta: number): string {
  if (delta > 0) return `+${delta}`;
  if (delta < 0) return `−${Math.abs(delta)}`;
  return "0";
}

/** The product's movements, newest first, as rows. Empty history gets a line that says what to do. */
export default function ProductMovementTable({ movements }: { movements: StockMovement[] }) {
  if (movements.length === 0) {
    return (
      <div className="flex flex-col items-start gap-2 border-t border-line-subtle pt-6">
        <p className="font-medium">No stock movements yet</p>
        <p className="text-secondary">
          Record a receipt, an issue or an adjustment above to start this product&apos;s history.
        </p>
      </div>
    );
  }

  return (
    <TableScroll label="Stock movements">
      <table className={tableClass}>
        <thead className="border-b border-line text-secondary">
          <tr>
            <th scope="col" className="px-4 py-2 font-medium">Type</th>
            <th scope="col" className="px-4 py-2 text-right font-medium">Change</th>
            <th scope="col" className="px-4 py-2 text-right font-medium">On hand after</th>
            <th scope="col" className="px-4 py-2 font-medium">Reason</th>
            <th scope="col" className="px-4 py-2 font-medium">Recorded by</th>
            <th scope="col" className="px-4 py-2 font-medium">When</th>
          </tr>
        </thead>
        <tbody>
          {movements.map((movement) => (
            <tr
              key={movement.id}
              className="h-14 border-b border-line-subtle transition-colors last:border-b-0 hover:bg-hover/60"
            >
              <td className="px-4 py-2 align-middle">{STOCK_MOVEMENT_LABELS[movement.type]}</td>
              <td className="px-4 py-2 text-right align-middle">{formatDelta(movement.quantityDelta)}</td>
              <td className="px-4 py-2 text-right align-middle">{movement.quantityAfter}</td>
              <td className="max-w-xs truncate px-4 py-2 align-middle text-secondary">
                {movement.reason ?? DASH}
              </td>
              <td className="px-4 py-2 align-middle text-secondary">
                {movement.createdBy?.fullName ?? "Removed user"}
              </td>
              <td className="px-4 py-2 align-middle text-secondary">
                <time dateTime={movement.createdAt}>{formatDateTime(movement.createdAt)}</time>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </TableScroll>
  );
}
