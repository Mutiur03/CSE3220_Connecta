// Client-side checks that mirror ProductCategory.NAME_MAX_LENGTH and DESCRIPTION_MAX_LENGTH on the
// backend. The backend validates again.
export const CATEGORY_NAME_MAX_LENGTH = 100;
export const CATEGORY_DESCRIPTION_MAX_LENGTH = 500;

export function validateCategoryName(name: string): string | undefined {
  if (!name) return "Enter a name for the category.";
  if (name.length > CATEGORY_NAME_MAX_LENGTH)
    return `Use at most ${CATEGORY_NAME_MAX_LENGTH} characters.`;
  return undefined;
}

export function validateCategoryDescription(
  description: string,
): string | undefined {
  if (description.length > CATEGORY_DESCRIPTION_MAX_LENGTH)
    return `Use at most ${CATEGORY_DESCRIPTION_MAX_LENGTH} characters.`;
  return undefined;
}
