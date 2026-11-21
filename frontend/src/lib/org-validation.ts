// Client-side checks so obvious mistakes never reach the backend. The backend validates again.
export const ORGANIZATION_NAME_MAX_LENGTH = 100;
export const SLUG_MAX_LENGTH = 60;
const SLUG_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const EMAIL_PATTERN = /^\S+@\S+\.\S+$/;
const EMAIL_MAX_LENGTH = 254;
const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function validateOrganizationName(name: string): string | undefined {
  if (!name) return "Enter a name for the organization.";
  if (name.length > ORGANIZATION_NAME_MAX_LENGTH)
    return `Use at most ${ORGANIZATION_NAME_MAX_LENGTH} characters.`;
  return undefined;
}

/** A URL-safe slug from a name: "Acme Studio!" becomes "acme-studio". Empty when no letters or digits. */
export function slugify(name: string): string {
  return name
    .normalize("NFKD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, SLUG_MAX_LENGTH)
    .replace(/-+$/, "");
}

export function validateSlug(slug: string): string | undefined {
  if (!slug) return "Enter a slug.";
  if (slug.length > SLUG_MAX_LENGTH)
    return `Use at most ${SLUG_MAX_LENGTH} characters.`;
  if (!SLUG_PATTERN.test(slug))
    return "Use lowercase letters, digits and single hyphens, such as acme-team.";
  return undefined;
}

export function validateInviteEmail(email: string): string | undefined {
  if (!email) return "Enter an email address.";
  if (email.length > EMAIL_MAX_LENGTH || !EMAIL_PATTERN.test(email))
    return "Enter a valid email address.";
  return undefined;
}

export const isUuid = (value: string) => UUID_PATTERN.test(value);
