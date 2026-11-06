import { CUSTOMER } from '../support/env';
import { expect, test } from '../support/fixtures';

test.describe('a customer', () => {
  test.use({ storageState: CUSTOMER.state });

  test('is refused the admin space, which they are not offered either', async ({ page }) => {
    await page.goto('/admin/produits');
    await expect(page.getByRole('heading', { name: 'Accès refusé' })).toBeVisible();
    await expect(page.getByRole('table')).toHaveCount(0);

    await page.getByRole('button', { name: `Menu du compte : ${CUSTOMER.name}` }).click();
    await expect(page.getByRole('menuitem', { name: 'Administration' })).toHaveCount(0);
  });
});

test('pages come with the security headers of nginx', async ({ page }) => {
  const response = await page.goto('/');
  const headers = response!.headers();
  expect(headers['content-security-policy']).toContain("script-src 'self'");
  expect(headers['x-content-type-options']).toBe('nosniff');
  // The session never reaches scripts: no token in the page's storage.
  const stored = await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }));
  expect(stored).not.toMatch(/token|eyJ/i);
});
