import Link from "next/link";
import type { StockMovement } from "@/lib/inventory-api";
import {
  formatMovementTime,
  formatQuantityDelta,
  movementTypeLabel,
} from "@/lib/inventory-view";
import { ArrowLeftRightIcon } from "./icons";
import { tableClass } from "./org-styles";
import { buttonClasses } from "./ui/classes";
import { TableScroll } from "./ui/table-scroll";

type Props = {
  movements: StockMovement[];
  productHref: (productId: string) => string;
  productsHref: string;
};

const DASH = "—";
const CELL = "px-4 py-2 align-middle";

// Newest first, as the API returns them. The product name opens that product, where its full
// movement history lives. Each row says what changed, why, and who did it.
export function StockMovementTable({
  movements,
  productHref,
  productsHref,
}: Props) {
  if (movements.length === 0) {
    return (
      <div className="flex flex-wrap items-start gap-x-4 gap-y-3 rounded-control border border-line p-4">
        <span className="grid size-10 shrink-0 place-items-center rounded-control border border-line bg-app text-primary">
          <ArrowLeftRightIcon />
        </span>
        <div className="flex min-w-0 flex-1 basis-56 flex-col gap-1">
          <p className="font-medium">No stock movements yet</p>
          <p className="text-sm text-secondary">
            Receive, issue or adjust stock on a product, and each change is
            recorded here.
          </p>
        </div>
        <Link href={productsHref} className={buttonClasses("secondary")}>
          Open products
        </Link>
      </div>
    );
  }

  return (
    <TableScroll label="Recent stock movements">
      <table className={tableClass}>
        <thead className="border-b border-line whitespace-nowrap text-secondary">
          <tr>
            <th scope="col" className="px-4 py-2 font-medium">
              Product
            </th>
            <th scope="col" className="px-4 py-2 font-medium">
              Type
            </th>
            <th scope="col" className="px-4 py-2 text-right font-medium">
              Change
            </th>
            <th scope="col" className="px-4 py-2 text-right font-medium">
              Stock after
            </th>
            <th scope="col" className="px-4 py-2 font-medium">
              By
            </th>
            <th scope="col" className="px-4 py-2 text-right font-medium">
              When
            </th>
          </tr>
        </thead>
        <tbody>
          {movements.map((movement) => (
            <tr
              key={movement.id}
              className="h-14 border-b border-line-subtle last:border-b-0"
            >
              <td className={CELL}>
                <Link
                  href={productHref(movement.productId)}
                  className="text-primary underline-offset-4 hover:underline"
                >
                  {movement.productName}
                </Link>
              </td>
              <td className={CELL}>
                <span className="flex flex-col">
                  <span>{movementTypeLabel(movement.type)}</span>
                  {movement.reason && (
                    <span
                      className="max-w-56 truncate text-caption text-secondary"
                      title={movement.reason}
                    >
                      {movement.reason}
                    </span>
                  )}
                </span>
              </td>
              <td
                className={`figure ${CELL} text-right ${movement.quantityDelta > 0 ? "text-success" : ""}`}
              >
                {formatQuantityDelta(movement.quantityDelta)}
              </td>
              <td className={`figure ${CELL} text-right`}>
                {movement.quantityAfter}
              </td>
              <td className={`${CELL} text-secondary`}>
                {movement.createdBy?.fullName ?? DASH}
              </td>
              <td
                className={`figure ${CELL} whitespace-nowrap text-right text-secondary`}
              >
                <time dateTime={movement.createdAt}>
                  {formatMovementTime(movement.createdAt)}
                </time>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </TableScroll>
  );
}
