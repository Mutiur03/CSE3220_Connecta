import { expect, test } from "@playwright/test";

test("login page renders the sign-in form", async ({ page }) => {
  await page.goto("/login");
  await expect(page.getByRole("heading", { name: "Connecta" })).toBeVisible();
  await expect(page.getByLabel("Email")).toBeVisible();
  await expect(page.getByLabel("Password")).toBeVisible();
});

test("home shows the landing page to a signed-out visitor under the same URL", async ({
  page,
}) => {
  await page.goto("/");
  await expect(page).toHaveURL(/\/$/);
  await expect(page.locator("#hero-title")).toBeVisible();
});

test("a protected page keeps its path and query in next", async ({ page }) => {
  await page.goto("/settings?tab=profile");
  await expect(page).toHaveURL(/\/login\?next=%2Fsettings%3Ftab%3Dprofile$/);
});

test("the invitation page stays public for a signed-out visitor", async ({
  page,
}) => {
  await page.goto("/invitations/accept?token=x");
  await expect(page).toHaveURL(/\/invitations\/accept\?token=x$/);
});
