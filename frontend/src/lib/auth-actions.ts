"use server";

import { redirect } from "next/navigation";
import {
  ApiError,
  login as apiLogin,
  logout as apiLogout,
  acceptInvitationAsNewUser as apiAcceptAsNewUser,
  register as apiRegister,
  setupOwner as apiSetupOwner,
} from "./api";
import { safeRedirectPath } from "./redirect";
import {
  clearSessionCookies,
  getRefreshToken,
  setSessionCookies,
} from "./session";
import {
  validateLogin,
  validateNewAccount,
  validateSignup,
  type LoginErrors,
  type NewAccountErrors,
  type SignupErrors,
} from "./validation";

export type LoginState = {
  errors?: LoginErrors;
  message?: string;
  /** Echoed back so the email field keeps its value after a failed attempt. */
  email?: string;
};

export async function login(
  _previous: LoginState,
  formData: FormData,
): Promise<LoginState> {
  const email = String(formData.get("email") ?? "").trim();
  const password = String(formData.get("password") ?? "");
  const next = safeRedirectPath(String(formData.get("next") ?? ""));

  const errors = validateLogin(email, password);
  if (Object.keys(errors).length > 0) return { errors, email };

  try {
    await setSessionCookies(await apiLogin(email, password));
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return { message: "Invalid email or password.", email };
    }
    if (error instanceof ApiError) {
      return { message: "Something went wrong. Please try again.", email };
    }
    return { message: "Cannot reach the server. Please try again.", email };
  }
  redirect(next);
}

export type SignupState = {
  errors?: SignupErrors;
  message?: string;
  /** Echoed back so the fields keep their values after a failed attempt. */
  fullName?: string;
  email?: string;
};

type CreateAccount = (
  email: string,
  password: string,
  fullName: string,
) => Promise<unknown>;

/**
 * Creates an account with `create`, then signs the new user in, so one submit leads straight into
 * the app. `onConflict` answers a 409 and differs by flow: signup says the email is taken, setup
 * means the system already has an owner.
 */
async function createAccountAndSignIn(
  formData: FormData,
  create: CreateAccount,
  onConflict: (echo: Pick<SignupState, "fullName" | "email">) => SignupState,
): Promise<SignupState> {
  const fullName = String(formData.get("fullName") ?? "").trim();
  const email = String(formData.get("email") ?? "").trim();
  const password = String(formData.get("password") ?? "");
  const next = safeRedirectPath(String(formData.get("next") ?? ""));
  const echo = { fullName, email };

  const errors = validateSignup(fullName, email, password);
  if (Object.keys(errors).length > 0) return { errors, ...echo };

  try {
    await create(email, password, fullName);
    await setSessionCookies(await apiLogin(email, password));
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) {
      return onConflict(echo);
    }
    if (error instanceof ApiError) {
      return { message: "Something went wrong. Please try again.", ...echo };
    }
    return { message: "Cannot reach the server. Please try again.", ...echo };
  }
  redirect(next);
}

export async function signup(
  _previous: SignupState,
  formData: FormData,
): Promise<SignupState> {
  return createAccountAndSignIn(formData, apiRegister, (echo) => ({
    errors: { email: "An account with this email already exists." },
    ...echo,
  }));
}

/** Creates the system owner on a fresh system. Once an owner exists, setup is over: go to signup. */
export async function setup(
  _previous: SignupState,
  formData: FormData,
): Promise<SignupState> {
  return createAccountAndSignIn(formData, apiSetupOwner, () =>
    redirect("/signup"),
  );
}

export type NewUserInvitationState = {
  errors?: NewAccountErrors;
  message?: string;
  fullName?: string;
};

/** An invited person with no account: creates it from the invitation, signs in and opens their organizations. */
export async function acceptInvitationAsNewUser(
  _previous: NewUserInvitationState,
  formData: FormData,
): Promise<NewUserInvitationState> {
  const token = String(formData.get("token") ?? "");
  const fullName = String(formData.get("fullName") ?? "").trim();
  const password = String(formData.get("password") ?? "");

  const errors = validateNewAccount(fullName, password);
  if (Object.keys(errors).length > 0) return { errors, fullName };

  try {
    await setSessionCookies(
      await apiAcceptAsNewUser(token, fullName, password),
    );
  } catch (error) {
    if (error instanceof ApiError) {
      return { message: invitationFailure(error.status), fullName };
    }
    return { message: "Cannot reach the server. Please try again.", fullName };
  }
  redirect("/organizations");
}

function invitationFailure(status: number): string {
  if (status === 409) {
    return "An account with this email already exists. Sign in to accept the invitation.";
  }
  if (status === 404 || status === 422) {
    return "This invitation is no longer valid. Ask your admin to send a new one.";
  }
  return "Something went wrong. Please try again.";
}

export async function logout() {
  const refreshToken = await getRefreshToken();
  if (refreshToken) {
    // If the backend cannot be reached we still sign out locally.
    await apiLogout(refreshToken).catch(() => {});
  }
  await clearSessionCookies();
  redirect("/login");
}
