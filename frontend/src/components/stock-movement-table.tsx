import Link from "next/link";
import type { StockMovement } from "@/lib/inventory-api";
import {
  formatMovementTime,
  formatQuantityDelta,
  movementTypeLabel,
} from "@/lib/inventory-view";
import { tableClass } from "./org-styles";
import { TableScroll } from "./ui/table-scroll";

type Props = {
  movements: StockMovement[];
  productHref: (productId: string) => string;
  productsHref: string;
};

const DASH = "—";

// Newest first, as the API returns them. The product name opens that product, where its full
// movement history lives.
export function StockMovementTable({ movements, productHref, productsHref }: Props) {
  if (movements.length === 0) {
    return (
      <div className="flex flex-col items-start gap-2 border-t border-line-subtle pt-3">
        <p className="font-medium">No stock movements yet</p>
        <p className="text-secondary">
          Receive, issue or adjust stock on a product, and each change is recorded here.
        </p>
        <Link href={productsHref} className="text-sm underline-offset-4 hover:underline">
          Open products
        </Link>
      </div>
    );
  }

  return (
    <TableScroll label="Recent stock movements">
      <table className={tableClass}>
        <thead className="border-b border-line text-secondary">
          <tr>
            <th scope="col" className="px-4 py-2 font-medium">When</th>
            <th scope="col" className="px-4 py-2 font-medium">Type</th>
            <th scope="col" className="px-4 py-2 font-medium">Product</th>
            <th scope="col" className="px-4 py-2 text-right font-medium">Change</th>
            <th scope="col" className="px-4 py-2 text-right font-medium">Stock after</th>
            <th scope="col" className="px-4 py-2 font-medium">By</th>
          </tr>
        </thead>
        <tbody>
          {movements.map((movement) => (
            <tr key={movement.id} className="h-14 border-b border-line-subtle last:border-b-0">
              <td className="px-4 py-2 align-middle text-secondary">
                {formatMovementTime(movement.createdAt)}
              </td>
              <td className="px-4 py-2 align-middle">{movementTypeLabel(movement.type)}</td>
              <td className="px-4 py-2 align-middle">
                <Link
                  href={productHref(movement.productId)}
                  className="font-medium underline-offset-4 hover:underline"
                >
                  {movement.productName}
                </Link>
              </td>
              <td className="px-4 py-2 text-right align-middle">
                {formatQuantityDelta(movement.quantityDelta)}
              </td>
              <td className="px-4 py-2 text-right align-middle">{movement.quantityAfter}</td>
              <td className="px-4 py-2 align-middle text-secondary">
                {movement.createdBy?.fullName ?? DASH}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </TableScroll>
  );
}
