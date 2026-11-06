import { adminApi, addToCart, createCategory, createProduct, customerApi, emptyCart, findCustomer, placeOrder, type Seeded } from '../support/api';
import { ADMIN, CUSTOMER, RUN } from '../support/env';
import { expect, expectAccessible, orderStatus, test } from '../support/fixtures';

/** WCAG 2.1 AA (axe) on each page, as displayed with its data, dialogs and panels included. */

let product: Seeded;
let order: Seeded;
let customer: Seeded;

test.beforeAll(async () => {
  const admin = await adminApi();
  const category = await createCategory(admin, `E2E Accessibilité ${RUN}`);
  product = await createProduct(admin, { name: `E2E Plaid ${RUN}`, categoryId: category.id, price: 49.9, stock: 20, description: 'Laine mérinos.' });
  customer = await findCustomer(admin, CUSTOMER.email);
  await admin.dispose();

  const alice = await customerApi();
  await emptyCart(alice);
  await addToCart(alice, product.id, 1);
  order = await placeOrder(alice);
  await addToCart(alice, product.id, 2); // a cart with a line for the cart page
  await alice.dispose();
});

test.describe('visitor', () => {
  test('catalogue', async ({ page }) => {
    // Searched by name: on a development database the first page may be full of earlier runs' products.
    await page.goto(`/?q=${encodeURIComponent(product.name)}`);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(page.getByRole('link', { name: product.name }).first()).toBeVisible();
    await expectAccessible(page, 'the catalogue');
  });

  test('product page', async ({ page }) => {
    await page.goto(`/produits/${product.id}`);
    await expect(page.getByRole('heading', { name: product.name })).toBeVisible();
    await expectAccessible(page, 'the product page');
  });
});

test.describe('customer', () => {
  test.use({ storageState: CUSTOMER.state });

  test('cart, and its confirmation dialog', async ({ page }) => {
    await page.goto('/panier');
    await expect(page.getByRole('group', { name: `Quantité de ${product.name}` })).toBeVisible();
    await expectAccessible(page, 'the cart');
    await page.getByRole('button', { name: 'Vider le panier' }).click();
    await expect(page.getByRole('dialog', { name: 'Vider le panier ?' })).toBeVisible();
    await expectAccessible(page, 'the dialog emptying the cart');
  });

  test('order tracking and history', async ({ page }) => {
    await page.goto(`/commandes/${order.id}`);
    await expect(orderStatus(page)).toHaveText('Confirmée', { timeout: 30_000 });
    await expectAccessible(page, 'the order tracking');
    await page.goto('/commandes');
    await expect(page.getByRole('table')).toBeVisible();
    await expectAccessible(page, 'the order history');
  });

  test('profile', async ({ page }) => {
    await page.goto('/profil');
    await expect(page.getByText(CUSTOMER.email)).toBeVisible();
    await expectAccessible(page, 'the profile');
  });
});

test.describe('administrator', () => {
  test.use({ storageState: ADMIN.state });

  test('products, and the product panel', async ({ page }) => {
    await page.goto('/admin/produits');
    await expect(page.getByRole('table')).toBeVisible();
    await expectAccessible(page, 'the admin products');
    await page.goto(`/admin/produits?produit=${product.id}`);
    await expect(page.getByRole('dialog', { name: `Modifier « ${product.name} »` }).getByRole('textbox', { name: 'Nom' })).toHaveValue(product.name);
    await expectAccessible(page, 'the product panel');
  });

  test('categories and best sellers', async ({ page }) => {
    await page.goto('/admin/categories');
    await expect(page.getByRole('list', { name: 'Catégories' })).toBeVisible();
    await expectAccessible(page, 'the admin categories');
    await page.goto('/admin/ventes');
    // A table, or "no sale yet" on a fresh stack (the API caches the ranking 5 minutes).
    await expect(page.getByText('Classement des commandes passées, mis à jour au plus toutes les 5 minutes.')).toBeVisible();
    await expectAccessible(page, 'the best sellers');
  });

  test('customers, and the customer panel', async ({ page }) => {
    await page.goto('/admin/clients');
    await expect(page.getByRole('table')).toBeVisible();
    await expectAccessible(page, 'the admin customers');
    await page.goto(`/admin/clients?client=${customer.id}`);
    await expect(page.getByRole('dialog', { name: CUSTOMER.name }).getByRole('heading', { name: 'Commandes' })).toBeVisible();
    await expectAccessible(page, 'the customer panel');
  });
});
