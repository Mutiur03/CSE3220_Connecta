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

// Limits mirror the backend's RegisterRequest.
export const FULL_NAME_MAX = 150;
export const EMAIL_MAX = 255;
export const PASSWORD_MIN = 8;
export const PASSWORD_MAX = 72;

export type NewAccountErrors = { fullName?: string; password?: string };
export type SignupErrors = LoginErrors & NewAccountErrors;

/** The name and password rules shared by signup, setup and accepting an invitation as a new user. */
export function validateNewAccount(
  fullName: string,
  password: string,
): NewAccountErrors {
  const errors: NewAccountErrors = {};
  if (!fullName) errors.fullName = "Enter your full name.";
  else if (fullName.length > FULL_NAME_MAX)
    errors.fullName = `Use ${FULL_NAME_MAX} characters or fewer.`;
  if (!password) errors.password = "Enter a password.";
  else if (password.length < PASSWORD_MIN)
    errors.password = `Use at least ${PASSWORD_MIN} characters.`;
  else if (password.length > PASSWORD_MAX)
    errors.password = `Use ${PASSWORD_MAX} characters or fewer.`;
  return errors;
}

export function validateSignup(
  fullName: string,
  email: string,
  password: string,
): SignupErrors {
  const errors: SignupErrors = validateNewAccount(fullName, password);
  if (!email) errors.email = "Enter your email address.";
  else if (!/^\S+@\S+\.\S+$/.test(email) || email.length > EMAIL_MAX)
    errors.email = "Enter a valid email address.";
  return errors;
}
