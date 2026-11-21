// Client-side checks that mirror Product.NAME_MAX_LENGTH and DESCRIPTION_MAX_LENGTH on the backend.
// The backend validates again.
export const PRODUCT_NAME_MAX_LENGTH = 150;
export const PRODUCT_DESCRIPTION_MAX_LENGTH = 1000;

export function validateProductName(name: string): string | undefined {
  if (!name) return "Enter a name for the product.";
  if (name.length > PRODUCT_NAME_MAX_LENGTH)
    return `Use at most ${PRODUCT_NAME_MAX_LENGTH} characters.`;
  return undefined;
}

export function validateProductDescription(
  description: string,
): string | undefined {
  if (description.length > PRODUCT_DESCRIPTION_MAX_LENGTH)
    return `Use at most ${PRODUCT_DESCRIPTION_MAX_LENGTH} characters.`;
  return undefined;
}
