"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { ApiError } from "./api";
import * as orgApi from "./org-api";
import { actionErrorMessage } from "./org-errors";
import { isOrgRole, type OrgRole } from "./org-permissions";
import { getAccessToken } from "./session";
import {
  validateInviteEmail,
  SLUG_MAX_LENGTH,
  slugify,
  validateOrganizationName,
} from "./org-validation";

const DASHBOARD = "/organizations";
const SLUG_TAKEN = 409;

/** The sidebar lists the caller's organizations: re-read it after one is created, left, joined or renamed. */
const refreshSidebar = () => revalidatePath("/", "layout");

/** What every organization form renders. Field errors and echoed values never carry a token. */
export type OrgActionState = {
  /** A failure to show in the form's alert line. */
  message?: string;
  /** A success line, such as "Name saved.". */
  notice?: string;
  errors?: Partial<Record<string, string>>;
  /** Echoed back after a failed attempt so the user does not retype the form. */
  values?: Partial<Record<string, string>>;
  /** The raw invitation token, set once right after an invitation is created. */
  token?: string;
};

type Outcome<T> =
  { ok: true; value: T } | { ok: false; message: string; status?: number };

const field = (formData: FormData, name: string) =>
  String(formData.get(name) ?? "").trim();

const hasErrors = (errors: Record<string, string | undefined>) =>
  Object.values(errors).some((error) => error !== undefined);

/** Runs one backend call; a failure becomes a message for the form instead of an exception. */
async function attempt<T>(call: () => Promise<T>): Promise<Outcome<T>> {
  try {
    return { ok: true, value: await call() };
  } catch (error) {
    return {
      ok: false,
      message: actionErrorMessage(error),
      status: error instanceof ApiError ? error.status : undefined,
    };
  }
}

const orgPath = (orgId: string) => `/organizations/${orgId}`;

export async function createOrganization(
  _previous: OrgActionState,
  formData: FormData,
): Promise<OrgActionState> {
  const name = field(formData, "name");
  const values = { name };
  const slug = slugify(name);
  const errors = {
    name:
      validateOrganizationName(name) ??
      (slug ? undefined : "Use at least one letter or digit."),
  };
  if (hasErrors(errors)) return { errors, values };

  const accessToken = await getAccessToken(DASHBOARD);
  let result = await attempt(() =>
    orgApi.createOrganization(accessToken, { name, slug }),
  );
  if (!result.ok && result.status === SLUG_TAKEN) {
    // Another organization has this slug: keep the name, add a short suffix.
    const suffix = Math.random().toString(36).slice(2, 6);
    const unique = `${slug.slice(0, SLUG_MAX_LENGTH - 5)}-${suffix}`;
    result = await attempt(() =>
      orgApi.createOrganization(accessToken, { name, slug: unique }),
    );
  }
  if (!result.ok) return { message: result.message, values };
  refreshSidebar();
  redirect(orgPath(result.value.id));
}

export async function renameOrganization(
  orgId: string,
  _previous: OrgActionState,
  formData: FormData,
): Promise<OrgActionState> {
  const name = field(formData, "name");
  const error = validateOrganizationName(name);
  if (error) return { errors: { name: error }, values: { name } };

  const accessToken = await getAccessToken(DASHBOARD);
  const result = await attempt(() =>
    orgApi.renameOrganization(accessToken, orgId, name),
  );
  if (!result.ok) return { message: result.message, values: { name } };
  refreshSidebar();
  return { notice: "Name saved." };
}

export async function deleteOrganization(
  orgId: string,
): Promise<OrgActionState> {
  const accessToken = await getAccessToken(DASHBOARD);
  const result = await attempt(() =>
    orgApi.deleteOrganization(accessToken, orgId),
  );
  if (!result.ok) return { message: result.message };
  refreshSidebar();
  redirect(DASHBOARD);
}

export async function changeMemberRole(
  orgId: string,
  membershipId: string,
  _previous: OrgActionState,
  formData: FormData,
): Promise<OrgActionState> {
  const role = field(formData, "role");
  if (!isOrgRole(role)) return { message: "Choose a valid role." };

  const accessToken = await getAccessToken(DASHBOARD);
  const result = await attempt(() =>
    orgApi.changeMemberRole(accessToken, orgId, membershipId, role),
  );
  if (!result.ok) return { message: result.message };
  revalidatePath(orgPath(orgId));
  return { notice: "Role updated." };
}

/** Removes a member, or leaves when `self` is true (then the caller goes back to the list). */
export async function removeMember(
  orgId: string,
  membershipId: string,
  self: boolean,
): Promise<OrgActionState> {
  const accessToken = await getAccessToken(DASHBOARD);
  const result = await attempt(() =>
    orgApi.removeMember(accessToken, orgId, membershipId),
  );
  if (!result.ok) return { message: result.message };
  if (self) {
    refreshSidebar();
    redirect(DASHBOARD);
  }
  revalidatePath(orgPath(orgId));
  return {};
}

export async function createInvitation(
  orgId: string,
  _previous: OrgActionState,
  formData: FormData,
): Promise<OrgActionState> {
  const email = field(formData, "email");
  const role = field(formData, "role");
  const values = { email, role };
  const errors = {
    email: validateInviteEmail(email),
    role: isOrgRole(role) ? undefined : "Choose a role.",
  };
  if (hasErrors(errors) || !isOrgRole(role)) return { errors, values };

  const accessToken = await getAccessToken(DASHBOARD);
  const result = await attempt(() =>
    orgApi.createInvitation(accessToken, orgId, {
      email,
      role: role as OrgRole,
    }),
  );
  if (!result.ok) return { message: result.message, values };
  revalidatePath(orgPath(orgId));
  return {
    notice: `Invitation created for ${email}.`,
    token: result.value.token,
  };
}

export async function revokeInvitation(
  orgId: string,
  invitationId: string,
): Promise<OrgActionState> {
  const accessToken = await getAccessToken(DASHBOARD);
  const result = await attempt(() =>
    orgApi.revokeInvitation(accessToken, orgId, invitationId),
  );
  if (!result.ok) return { message: result.message };
  revalidatePath(orgPath(orgId));
  return {};
}

export async function acceptInvitation(
  _previous: OrgActionState,
  formData: FormData,
): Promise<OrgActionState> {
  const token = field(formData, "token");
  if (!token) {
    return {
      errors: { token: "Enter the invitation token." },
      values: { token },
    };
  }

  const accessToken = await getAccessToken(DASHBOARD);
  const result = await attempt(() =>
    orgApi.acceptInvitation(accessToken, token),
  );
  if (!result.ok) return { message: result.message, values: { token } };
  refreshSidebar();
  redirect(orgPath(result.value.id));
}
