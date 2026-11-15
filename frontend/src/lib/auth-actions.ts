"use server";

import { redirect } from "next/navigation";
import { ApiError, login as apiLogin, logout as apiLogout } from "./api";
import {
  clearSessionCookies,
  getRefreshToken,
  setSessionCookies,
} from "./session";
import { validateLogin, type LoginErrors } from "./validation";

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
  redirect("/");
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
