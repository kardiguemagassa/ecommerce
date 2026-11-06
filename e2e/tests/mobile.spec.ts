import { adminApi, createCategory, createProduct, customerApi, emptyCart, type Seeded } from '../support/api';
import { CUSTOMER, RUN } from '../support/env';
import { expect, expectAccessible, orderStatus, test } from '../support/fixtures';

test.use({ storageState: CUSTOMER.state });

let product: Seeded;

test.beforeAll(async () => {
  const admin = await adminApi();
  const category = await createCategory(admin, `E2E Mobile ${RUN}`);
  product = await createProduct(admin, { name: `E2E Tapis ${RUN}`, categoryId: category.id, price: 29.9, stock: 6 });
  await admin.dispose();
  const alice = await customerApi();
  await emptyCart(alice);
  await alice.dispose();
});

/** No page wider than the screen: a horizontal scroll hides content and buttons on a phone. */
async function expectNoHorizontalScroll(page: import('@playwright/test').Page) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow, 'horizontal overflow in pixels').toBeLessThanOrEqual(0);
}

test('on a phone, a customer finds a product, orders it and follows the order', async ({ page }) => {
  await page.goto(`/?q=${encodeURIComponent(product.name)}`);
  await expectNoHorizontalScroll(page);
  await page.getByRole('link', { name: product.name }).first().click();

  await page.getByRole('button', { name: 'Ajouter au panier' }).click();
  await expect(page.getByRole('link', { name: 'Panier, 1 article' })).toBeVisible();
  await page.getByRole('link', { name: 'Panier, 1 article' }).click();
  await expect(page.getByRole('group', { name: `Quantité de ${product.name}` })).toBeVisible();
  await expectNoHorizontalScroll(page);
  await expectAccessible(page, 'the cart on a phone');

  await page.getByRole('button', { name: 'Passer la commande' }).click();
  await expect(orderStatus(page)).toHaveText('Confirmée', { timeout: 30_000 });
  await expectNoHorizontalScroll(page);
});
