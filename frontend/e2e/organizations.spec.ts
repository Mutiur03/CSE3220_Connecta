import { expect, test } from "@playwright/test";

test("organization pages redirect a signed-out visitor to login", async ({
  page,
}) => {
  await page.goto("/organizations");
  await expect(page).toHaveURL(/\/login$/);
});
