import { expect, test as setup, type Page } from '@playwright/test';
import { ADMIN, CUSTOMER, setting } from '../support/env';

/**
 * Signs each demo account in once, through the real Keycloak form (BFF login: the browser only gets a session cookie),
 * and keeps the session for the tests.
 */
async function signIn(page: Page, email: string, statePath: string) {
  await page.goto('/');
  await page.getByRole('button', { name: 'Se connecter' }).first().click();

  await page.waitForURL(/\/realms\/ecommerce\//);
  await page.locator('#username').fill(email);
  await page.locator('#password').fill(setting('DEMO_USER_PASSWORD'));
  await page.locator('#kc-login').click();

  await expect(page.getByRole('button', { name: /^Menu du compte : / })).toBeVisible();
  await page.context().storageState({ path: statePath });
}

setup('sign in the customer', async ({ page }) => {
  await signIn(page, CUSTOMER.email, CUSTOMER.state);
});

setup('sign in the administrator', async ({ page }) => {
  await signIn(page, ADMIN.email(), ADMIN.state);
});
