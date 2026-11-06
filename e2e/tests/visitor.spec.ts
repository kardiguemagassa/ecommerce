import { adminApi, createCategory, createProduct, type Seeded } from '../support/api';
import { RUN } from '../support/env';
import { expect, test } from '../support/fixtures';

let category: Seeded;
let product: Seeded;

test.beforeAll(async () => {
  const api = await adminApi();
  category = await createCategory(api, `E2E Visite ${RUN}`);
  product = await createProduct(api, { name: `E2E Gourde ${RUN}`, categoryId: category.id, price: 24.9, stock: 8 });
  await api.dispose();
});

test('a visitor browses the catalogue by category, opens a product, and is offered to sign in to buy it', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveTitle('Catalogue · Comptoir');

  await page.getByRole('button', { name: category.name }).click();
  await expect(page).toHaveURL(/categorie=/);
  await page.getByRole('link', { name: product.name }).first().click();

  await expect(page.getByRole('heading', { name: product.name, level: 1 })).toBeVisible();
  await expect(page.getByText('24,90 €').first()).toBeVisible();
  await page.getByRole('button', { name: 'Ajouter au panier' }).click();

  await expect(page.getByText('Connectez-vous pour ajouter des produits au panier.')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Panier vide' })).toHaveCount(0); // no cart for a visitor
});

test('a visitor asking for the cart is asked to sign in', async ({ page }) => {
  await page.goto('/panier');
  await expect(page.getByRole('heading', { name: 'Connexion requise' })).toBeVisible();
});
