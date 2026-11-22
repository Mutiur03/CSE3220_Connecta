import { STOCK_STATUS_LABELS, type StockStatus } from "@/lib/product-validation";
import type { BadgeTone } from "./ui/classes";
import { Badge } from "./ui/badge";

// Out of stock takes the danger tint. Low stock is marked by the brass status dot, the brief's
// only use of the highlight colour. In stock stays neutral so the chrome stays quiet.
const TONE: Record<StockStatus, BadgeTone> = {
  IN_STOCK: "neutral",
  LOW_STOCK: "neutral",
  OUT_OF_STOCK: "danger",
};

export function StockStatusBadge({ status }: { status: StockStatus }) {
  return (
    <Badge tone={TONE[status]} dot={status === "LOW_STOCK"}>
      {STOCK_STATUS_LABELS[status]}
    </Badge>
  );
}
