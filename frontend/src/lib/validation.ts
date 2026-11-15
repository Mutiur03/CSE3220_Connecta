export type LoginErrors = { email?: string; password?: string };

/** Client-side checks so obvious mistakes never reach the backend. The backend validates again. */
export function validateLogin(email: string, password: string): LoginErrors {
  const errors: LoginErrors = {};
  if (!email) errors.email = "Enter your email address.";
  else if (!/^\S+@\S+\.\S+$/.test(email))
    errors.email = "Enter a valid email address.";
  if (!password) errors.password = "Enter your password.";
  return errors;
}
