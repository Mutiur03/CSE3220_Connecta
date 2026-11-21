// Client-side checks that mirror the stock movement rules on the backend. The backend validates again.
export const STOCK_MOVEMENT_TYPES = ["RECEIVE", "ISSUE", "ADJUST"] as const;
export type StockMovementType = (typeof STOCK_MOVEMENT_TYPES)[number];

export const STOCK_MOVEMENT_LABELS: Record<StockMovementType, string> = {
  RECEIVE: "Receive",
  ISSUE: "Issue",
  ADJUST: "Adjust",
};

export const STOCK_REASON_MAX_LENGTH = 255;
const STOCK_QUANTITY_MAX = 2_147_483_647;
const WHOLE_NUMBER = /^-?\d+$/;

/** RECEIVE and ISSUE move at least one unit. ADJUST sets a level, so 0 is allowed. */
const MINIMUM_QUANTITY: Record<StockMovementType, number> = {
  RECEIVE: 1,
  ISSUE: 1,
  ADJUST: 0,
};

/** Shown on the quantity field when the backend refuses an ISSUE larger than the stock on hand. */
export const SHORT_STOCK_MESSAGE =
  "Not enough stock. Issue no more than the units on hand.";

export function isStockMovementType(value: string): value is StockMovementType {
  return (STOCK_MOVEMENT_TYPES as readonly string[]).includes(value);
}

export function validateStockQuantity(
  type: StockMovementType,
  quantity: string,
): string | undefined {
  if (quantity === "") return "Enter a quantity.";
  if (!WHOLE_NUMBER.test(quantity)) return "Enter a whole number, such as 12.";
  const value = Number(quantity);
  if (value < MINIMUM_QUANTITY[type]) {
    return type === "ADJUST"
      ? "Enter a stock level of 0 or more."
      : "Enter a quantity of at least 1.";
  }
  if (value > STOCK_QUANTITY_MAX) return `Use a number up to ${STOCK_QUANTITY_MAX}.`;
  return undefined;
}

export function validateStockReason(reason: string): string | undefined {
  if (reason.length > STOCK_REASON_MAX_LENGTH)
    return `Use at most ${STOCK_REASON_MAX_LENGTH} characters.`;
  return undefined;
}
