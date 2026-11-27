import { expect, test, type Page } from "@playwright/test";

/**
 * Critical signed-in flows. They need a running stack (frontend and backend) with an account that
 * can reach an organization with at least one product and one conversation. Nothing is seeded here:
 * point the specs at your own data with
 *
 *   E2E_EMAIL, E2E_PASSWORD   credentials of an ADMIN of that organization
 *   E2E_ORG_ID                the organization id (its /organizations/<id> URL segment)
 *
 * Without all three the whole file is skipped. CI does not run e2e, so it never needs them.
 * See docs/frontend-testing.md.
 */
const email = process.env.E2E_EMAIL;
const password = process.env.E2E_PASSWORD;
const orgId = process.env.E2E_ORG_ID;

test.skip(
  !email || !password || !orgId,
  "Set E2E_EMAIL, E2E_PASSWORD and E2E_ORG_ID to run the signed-in workflow specs.",
);

async function signIn(page: Page) {
  await page.goto("/login");
  await page.getByLabel("Email").fill(email!);
  await page.getByLabel("Password", { exact: true }).fill(password!);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page).not.toHaveURL(/\/login/);
}

async function openFirstConversation(page: Page) {
  await page.goto(`/organizations/${orgId}/inbox`);
  const first = page
    .getByRole("list", { name: "Conversations" })
    .getByRole("link")
    .first();
  await expect(first, "the organization needs at least one conversation").toBeVisible();
  await first.click();
  await expect(page).toHaveURL(new RegExp(`/organizations/${orgId}/inbox/[^/]+`));
}

test("a wrong password is refused and keeps the visitor on the login page", async ({
  page,
}) => {
  await page.goto("/login");
  await page.getByLabel("Email").fill(email!);
  await page.getByLabel("Password", { exact: true }).fill(`${password}-wrong`);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByRole("alert")).toBeVisible();
  await expect(page).toHaveURL(/\/login/);
});

test("sign in lands in the signed-in shell", async ({ page }) => {
  await signIn(page);
  await page.goto(`/organizations/${orgId}`);
  await expect(page).toHaveURL(new RegExp(`/organizations/${orgId}`));
});

test("create a product and see it in the list", async ({ page }) => {
  const name = `E2E mug ${Date.now()}`;
  await signIn(page);
  await page.goto(`/organizations/${orgId}/products/new`);

  await page.getByLabel("Name").fill(name);
  await page.getByLabel("Unit price").fill("9.99");
  await page.getByLabel("Reorder level").fill("3");
  await page.getByRole("button", { name: "Create product" }).click();

  await expect(page).toHaveURL(new RegExp(`/organizations/${orgId}/products$`));
  await expect(page.getByText(name).first()).toBeVisible();
});

test("an invalid product shows the error on its field", async ({ page }) => {
  await signIn(page);
  await page.goto(`/organizations/${orgId}/products/new`);
  await page.getByRole("button", { name: "Create product" }).click();
  await expect(page.getByLabel("Name")).toHaveAttribute("aria-invalid", "true");
});

test("open a conversation and assign it", async ({ page }) => {
  await signIn(page);
  await openFirstConversation(page);

  const assignee = page.getByLabel("Assignee").first();
  const options = await assignee.locator("option").all();
  test.skip(options.length < 2, "needs an assignable member besides Unassigned");
  const target = await options[1].getAttribute("value");
  await assignee.selectOption(target!);
  await expect(assignee).toHaveValue(target!);
});

test("share a product into a conversation", async ({ page }) => {
  await signIn(page);
  await openFirstConversation(page);

  await page.getByRole("button", { name: "Share a product" }).click();
  const dialog = page.getByRole("dialog", { name: "Share a product" });
  await expect(dialog).toBeVisible();

  await dialog.getByRole("searchbox", { name: /Search products/ }).fill("");
  await dialog.getByRole("button", { name: "Search" }).click();
  const firstProduct = dialog.getByRole("list").getByRole("button").first();
  await expect(firstProduct, "the organization needs at least one product").toBeVisible();
  await firstProduct.click();

  await expect(dialog.getByLabel("Message")).not.toBeEmpty();
  await dialog.getByRole("button", { name: "Send" }).click();
  await expect(dialog).toBeHidden();
});
