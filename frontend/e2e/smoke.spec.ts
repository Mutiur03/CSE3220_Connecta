import { expect, test } from "@playwright/test";

test("login page renders the sign-in form", async ({ page }) => {
  await page.goto("/login");
  await expect(page.getByRole("heading", { name: "Connecta" })).toBeVisible();
  await expect(page.getByLabel("Email")).toBeVisible();
  await expect(page.getByLabel("Password")).toBeVisible();
});

test("home redirects a signed-out visitor to login", async ({ page }) => {
  await page.goto("/");
  await expect(page).toHaveURL(/\/login$/);
});

test("a protected page keeps its path and query in next", async ({ page }) => {
  await page.goto("/invitations/accept?token=x");
  await expect(page).toHaveURL(
    /\/login\?next=%2Finvitations%2Faccept%3Ftoken%3Dx$/,
  );
});
